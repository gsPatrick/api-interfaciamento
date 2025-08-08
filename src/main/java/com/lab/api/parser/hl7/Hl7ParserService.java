package com.lab.api.parser.hl7;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import com.lab.api.domain.hl7.Hl7Message;
import com.lab.api.domain.hl7.Hl7Order;
import com.lab.api.domain.hl7.Hl7Patient;
import com.lab.api.domain.hl7.Hl7Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class Hl7ParserService {

    private static final char VT = 0x0b;
    private static final char FS = 0x1c;
    private static final char CR = 0x0d;

    public Hl7Message parse(String rawMllpMessage, Map<String, String> hints) {
        log.info("Iniciando parse da mensagem HL7...");

        String pureHl7Message = unwrapMllp(rawMllpMessage);
        if (pureHl7Message.isEmpty()) {
            log.error("A mensagem HL7 está vazia após remover o wrapper MLLP.");
            return null;
        }

        // Log da mensagem pura para debug
        log.debug("Mensagem HL7 pura: {}", pureHl7Message);

        try {
            // Primeiro tenta o método HAPI padrão
            return tentarParseComHapi(pureHl7Message, hints);
        } catch (Exception e) {
            log.warn("Parse HAPI falhou, tentando parse manual: {}", e.getMessage());
            // Se falhar, usa o parser manual robusto
            return parseManual(pureHl7Message, hints);
        }
    }

    private Hl7Message tentarParseComHapi(String pureHl7Message, Map<String, String> hints) throws Exception {
        // Pre-process the message to fix missing segment separators
        String processedMessage = preProcessMessage(pureHl7Message);

        try (HapiContext context = new DefaultHapiContext()) {
            context.setValidationContext(new NoValidation());

            PipeParser parser = context.getPipeParser();
            Message hapiMessage = parser.parse(processedMessage);
            Terser terser = new Terser(hapiMessage);

            Hl7Message message = new Hl7Message();

            popularMsh(message, terser);
            popularPidComTerser(message, terser);
            popularSpmEObr(message, terser, hints);
            popularObxComTerser(message, terser);

            // Check if we got meaningful data - if not, throw exception to trigger manual parsing
            if (isMessageEmpty(message)) {
                log.warn("HAPI parsing produced empty results, forcing manual parsing");
                throw new Exception("HAPI parsing produced no meaningful data");
            }

            // Log detalhado dos dados parseados
            log.info("=== DADOS PARSEADOS COM HAPI ===");
            log.info("ID de Controle: {}", message.getMessageControlId());
            log.info("Aplicação: {}", message.getSendingApplication());

            if (message.getPatient() != null) {
                log.info("Paciente - ID: {}, Nome: {} {}, Data Nascimento: {}",
                        message.getPatient().getPatientId(),
                        message.getPatient().getFirstName(),
                        message.getPatient().getLastName(),
                        message.getPatient().getBirthDate());
            } else {
                log.warn("PACIENTE É NULL!");
            }

            if (message.getOrder() != null) {
                log.info("Ordem - Amostra: {}, Serviço: {} ({})",
                        message.getOrder().getSpecimenId(),
                        message.getOrder().getUniversalServiceId(),
                        message.getOrder().getUniversalServiceText());
            } else {
                log.warn("ORDEM É NULL!");
            }

            if (message.getResults() != null && !message.getResults().isEmpty()) {
                log.info("Resultados encontrados: {}", message.getResults().size());
                for (int i = 0; i < message.getResults().size(); i++) {
                    Hl7Result result = message.getResults().get(i);
                    log.info("Resultado {}: Teste {} ({}), Valor: {}, Unidade: {}",
                            i + 1, result.getTestId(), result.getTestName(),
                            result.getValue(), result.getUnits());
                }
            } else {
                log.warn("NENHUM RESULTADO ENCONTRADO!");
            }
            log.info("=== FIM DOS DADOS PARSEADOS ===");

            return message;
        }
    }

    /**
     * Pre-processes the HL7 message to fix common formatting issues
     */
    private String preProcessMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Check if message lacks proper segment separators (segments run together)
        if (!message.contains("\r") && !message.contains("\n")) {
            // Look for segment headers (3 uppercase letters followed by |) and add separators
            String processed = message.replaceAll("([A-Z]{3}\\|)", "\r$1");
            // Remove the leading \r from the first segment
            if (processed.startsWith("\r")) {
                processed = processed.substring(1);
            }
            log.debug("Pre-processed message to add segment separators: {}", processed);
            return processed;
        }

        return message;
    }

    /**
     * Checks if the parsed message contains meaningful data
     */
    private boolean isMessageEmpty(Hl7Message message) {
        // Check if we have patient data
        boolean hasPatientData = message.getPatient() != null &&
                (message.getPatient().getPatientId() != null && !message.getPatient().getPatientId().trim().isEmpty());

        // Check if we have order data
        boolean hasOrderData = message.getOrder() != null &&
                (message.getOrder().getSpecimenId() != null && !message.getOrder().getSpecimenId().trim().isEmpty());

        // Check if we have results
        boolean hasResults = message.getResults() != null && !message.getResults().isEmpty();

        // Message is considered empty if it has no patient data, no order data, and no results
        return !hasPatientData && !hasOrderData && !hasResults;
    }

    private Hl7Message parseManual(String pureHl7Message, Map<String, String> hints) {
        log.info("Iniciando parse manual da mensagem HL7...");

        // Extrai os segmentos manualmente usando regex
        Map<String, List<String[]>> segmentos = extrairSegmentos(pureHl7Message);

        Hl7Message message = new Hl7Message();

        // Parse MSH
        if (segmentos.containsKey("MSH")) {
            String[] mshCampos = segmentos.get("MSH").get(0);
            if (mshCampos.length > 3) message.setSendingApplication(mshCampos[3]);
            if (mshCampos.length > 10) message.setMessageControlId(mshCampos[10]);
        }

        // Parse PID
        if (segmentos.containsKey("PID")) {
            String[] pidCampos = segmentos.get("PID").get(0);
            message.setPatient(Hl7Patient.builder()
                    .patientId(obterCampo(pidCampos, 3, 1))
                    .lastName(obterCampo(pidCampos, 5, 1))
                    .firstName(obterCampo(pidCampos, 5, 2))
                    .birthDate(obterCampo(pidCampos, 7, 1))
                    .build());
        } else {
            message.setPatient(Hl7Patient.builder().build());
        }

        // Parse SPM/OBR
        String idAmostra = null;
        if (segmentos.containsKey("SPM")) {
            String[] spmCampos = segmentos.get("SPM").get(0);
            idAmostra = obterCampo(spmCampos, 2, 1);
        }

        String universalServiceId = null;
        String universalServiceText = null;
        if (segmentos.containsKey("OBR")) {
            String[] obrCampos = segmentos.get("OBR").get(0);
            if (idAmostra == null) idAmostra = obterCampo(obrCampos, 2, 1);
            if (idAmostra == null) idAmostra = obterCampo(obrCampos, 3, 1);
            universalServiceId = obterCampo(obrCampos, 4, 1);
            universalServiceText = obterCampo(obrCampos, 4, 2);
        }

        message.setOrder(Hl7Order.builder()
                .specimenId(idAmostra)
                .universalServiceId(universalServiceId)
                .universalServiceText(universalServiceText)
                .build());

        // Parse OBX
        if (segmentos.containsKey("OBX")) {
            for (String[] obxCampos : segmentos.get("OBX")) {
                String testId = obterCampo(obxCampos, 3, 1);
                if (testId == null || testId.isBlank()) {
                    testId = obterCampo(obxCampos, 3, 2);
                }

                message.addResult(Hl7Result.builder()
                        .testId(testId)
                        .testName(obterCampo(obxCampos, 3, 2))
                        .value(obterCampo(obxCampos, 5, 1))
                        .units(obterCampo(obxCampos, 6, 1))
                        .referenceRange(obterCampo(obxCampos, 7, 1))
                        .abnormalFlags(obterCampo(obxCampos, 8, 1))
                        .build());
            }
        }

        // Log detalhado dos dados parseados
        log.info("=== DADOS PARSEADOS MANUALMENTE ===");
        log.info("ID de Controle: {}", message.getMessageControlId());
        log.info("Aplicação: {}", message.getSendingApplication());

        if (message.getPatient() != null) {
            log.info("Paciente - ID: {}, Nome: {} {}, Data Nascimento: {}",
                    message.getPatient().getPatientId(),
                    message.getPatient().getFirstName(),
                    message.getPatient().getLastName(),
                    message.getPatient().getBirthDate());
        } else {
            log.warn("PACIENTE É NULL!");
        }

        if (message.getOrder() != null) {
            log.info("Ordem - Amostra: {}, Serviço: {} ({})",
                    message.getOrder().getSpecimenId(),
                    message.getOrder().getUniversalServiceId(),
                    message.getOrder().getUniversalServiceText());
        } else {
            log.warn("ORDEM É NULL!");
        }

        if (message.getResults() != null && !message.getResults().isEmpty()) {
            log.info("Resultados encontrados: {}", message.getResults().size());
            for (int i = 0; i < message.getResults().size(); i++) {
                Hl7Result result = message.getResults().get(i);
                log.info("Resultado {}: Teste {} ({}), Valor: {}, Unidade: {}",
                        i + 1, result.getTestId(), result.getTestName(),
                        result.getValue(), result.getUnits());
            }
        } else {
            log.warn("NENHUM RESULTADO ENCONTRADO!");
        }
        log.info("=== FIM DOS DADOS PARSEADOS MANUALMENTE ===");

        return message;
    }

    private Map<String, List<String[]>> extrairSegmentos(String mensagem) {
        Map<String, List<String[]>> segmentos = new HashMap<>();

        // First try to split by carriage returns if they exist
        if (mensagem.contains("\r") || mensagem.contains("\n")) {
            String[] linhas = mensagem.split("[\r\n]+");
            for (String linha : linhas) {
                if (linha.trim().isEmpty()) continue;

                String[] campos = linha.split("\\|");
                if (campos.length > 0 && campos[0].matches("[A-Z]{3}")) {
                    String tipoSegmento = campos[0];
                    segmentos.computeIfAbsent(tipoSegmento, k -> new ArrayList<>()).add(campos);
                    log.debug("Segmento encontrado (linha): {} -> {}", tipoSegmento, linha);
                }
            }
        } else {
            // Fallback: Use regex for concatenated message (no proper segment separators)
            log.debug("Mensagem sem separadores de segmento detectada, usando parsing por regex");

            // Improved regex to handle segments that run together
            Pattern pattern = Pattern.compile("([A-Z]{3})\\|([^A-Z]*?)(?=(?:[A-Z]{3}\\||$))");
            Matcher matcher = pattern.matcher(mensagem);

            while (matcher.find()) {
                String tipoSegmento = matcher.group(1);
                String conteudoSegmento = tipoSegmento + "|" + matcher.group(2);

                log.debug("Segmento encontrado (regex): {} -> {}", tipoSegmento, conteudoSegmento);

                // Divide o segmento em campos (separados por |)
                String[] campos = conteudoSegmento.split("\\|");

                segmentos.computeIfAbsent(tipoSegmento, k -> new ArrayList<>()).add(campos);
            }
        }

        // Log summary of found segments
        log.debug("Segmentos extraídos: {}", segmentos.keySet());

        return segmentos;
    }

    private String obterCampo(String[] campos, int indice, int componente) {
        if (campos == null || indice >= campos.length) {
            log.trace("Campo não encontrado: índice {} fora do range (total: {})", indice, campos != null ? campos.length : 0);
            return null;
        }

        String campo = campos[indice];
        if (campo == null || campo.trim().isEmpty()) {
            log.trace("Campo vazio no índice {}", indice);
            return null;
        }

        // Se há componentes (separados por ^)
        if (campo.contains("^")) {
            String[] componentes = campo.split("\\^");
            if (componente > 0 && componente <= componentes.length) {
                String valor = componentes[componente - 1].trim();
                log.trace("Campo[{}][{}] = '{}'", indice, componente, valor);
                return valor.isEmpty() ? null : valor;
            }
        } else if (componente == 1) {
            // Se não há componentes e queremos o primeiro, retorna o campo completo
            String valor = campo.trim();
            log.trace("Campo[{}] = '{}'", indice, valor);
            return valor.isEmpty() ? null : valor;
        }

        log.trace("Componente {} não encontrado no campo[{}] = '{}'", componente, indice, campo);
        return null;
    }

    // Métodos HAPI originais (para quando o parse HAPI funciona)
    private void popularMsh(Hl7Message message, Terser terser) throws HL7Exception {
        message.setSendingApplication(obterCampoSeguro(terser, "/MSH-3-1"));
        message.setMessageControlId(obterCampoSeguro(terser, "/MSH-10-1"));
    }

    private void popularPidComTerser(Hl7Message message, Terser terser) {
        String patientId = obterCampoSeguro(terser, "/PID-3-1");
        String lastName = obterCampoSeguro(terser, "/PID-5-1");
        String firstName = obterCampoSeguro(terser, "/PID-5-2");
        String birthDate = obterCampoSeguro(terser, "/PID-7-1");

        log.debug("PID extraído - ID: {}, Sobrenome: {}, Nome: {}, Nascimento: {}",
                patientId, lastName, firstName, birthDate);

        message.setPatient(Hl7Patient.builder()
                .patientId(patientId)
                .lastName(lastName)
                .firstName(firstName)
                .birthDate(birthDate)
                .build());
    }

    private void popularSpmEObr(Hl7Message message, Terser terser, Map<String, String> hints) {
        String idAmostra = null;
        String localizacaoIdAmostra = hints != null ? hints.getOrDefault("sampleIdLocation", "SPM_2") : "SPM_2";

        switch (localizacaoIdAmostra.toUpperCase()) {
            case "SPM_1": idAmostra = obterCampoSeguro(terser, "/SPM-1-1"); break;
            case "SPM_2": idAmostra = obterCampoSeguro(terser, "/SPM-2-1"); break;
            case "OBR_3": idAmostra = obterCampoSeguro(terser, "/OBR-3-1"); break;
            case "OBR_2": idAmostra = obterCampoSeguro(terser, "/OBR-2-1"); break;
        }

        // Fallback
        if (idAmostra == null || idAmostra.isBlank()) idAmostra = obterCampoSeguro(terser, "/SPM-2-1");
        if (idAmostra == null || idAmostra.isBlank()) idAmostra = obterCampoSeguro(terser, "/OBR-2-1");
        if (idAmostra == null || idAmostra.isBlank()) idAmostra = obterCampoSeguro(terser, "/OBR-3-1");

        String universalServiceId = obterCampoSeguro(terser, "/OBR-4-1");
        String universalServiceText = obterCampoSeguro(terser, "/OBR-4-2");

        log.debug("SPM/OBR extraído - Amostra: {}, Serviço: {} ({})",
                idAmostra, universalServiceId, universalServiceText);

        message.setOrder(Hl7Order.builder()
                .specimenId(idAmostra)
                .universalServiceId(universalServiceId)
                .universalServiceText(universalServiceText)
                .build());
    }

    private void popularObxComTerser(Hl7Message message, Terser terser) {
        int contadorResultados = 0;
        for (int i = 0; ; i++) {
            try {
                String caminhoBase = "/OBX(" + i + ")-";
                String idTeste = obterCampoSeguro(terser, caminhoBase + "3-1");
                if (idTeste == null || idTeste.isBlank()) {
                    idTeste = obterCampoSeguro(terser, caminhoBase + "3-2");
                }

                if (idTeste == null || idTeste.isBlank()) {
                    break; // Não há mais segmentos OBX
                }

                String testName = obterCampoSeguro(terser, caminhoBase + "3-2");
                String value = obterCampoSeguro(terser, caminhoBase + "5-1");
                String units = obterCampoSeguro(terser, caminhoBase + "6-1");
                String referenceRange = obterCampoSeguro(terser, caminhoBase + "7-1");
                String abnormalFlags = obterCampoSeguro(terser, caminhoBase + "8-1");

                log.debug("OBX[{}] extraído - ID: {}, Nome: {}, Valor: {}, Unidade: {}",
                        i, idTeste, testName, value, units);

                message.addResult(Hl7Result.builder()
                        .testId(idTeste)
                        .testName(testName)
                        .value(value)
                        .units(units)
                        .referenceRange(referenceRange)
                        .abnormalFlags(abnormalFlags)
                        .build());

                contadorResultados++;
            } catch (Exception e) {
                break; // Fim dos segmentos OBX
            }
        }
        log.debug("Total de resultados OBX extraídos: {}", contadorResultados);
    }

    private String obterCampoSeguro(Terser terser, String caminho) {
        try {
            String valor = terser.get(caminho);
            log.debug("Campo {} = '{}'", caminho, valor);
            return valor;
        } catch (Exception e) {
            log.debug("Não foi possível obter o campo no caminho {}: {}", caminho, e.getMessage());
            return null;
        }
    }

    private String unwrapMllp(String mllpMessage) {
        if (mllpMessage == null || mllpMessage.length() < 3) return "";
        int startIndex = mllpMessage.indexOf(VT);
        int endIndex = mllpMessage.lastIndexOf(FS);
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return mllpMessage.substring(startIndex + 1, endIndex);
        }
        return mllpMessage;
    }
}