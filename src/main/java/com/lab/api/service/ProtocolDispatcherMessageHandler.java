package com.lab.api.service;

import com.lab.api.config.EquipmentConfig;
import com.lab.api.domain.astm.AstmMessage;
import com.lab.api.domain.hl7.Hl7Message;
import com.lab.api.domain.integra.IntegraMessage;
import com.lab.api.integration.common.MessageHandler;
import com.lab.api.parser.astm.AstmParserService;
import com.lab.api.parser.hl7.Hl7ParserService;
import com.lab.api.parser.integra.IntegraParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service("protocolDispatcher")
@Primary
@RequiredArgsConstructor
@Slf4j
public class ProtocolDispatcherMessageHandler implements MessageHandler {

    private final AstmParserService astmParserService;
    private final Hl7ParserService hl7ParserService;
    private final IntegraParserService integraParserService;
    private final LabOrderService labOrderService;
    private final HostQueryService hostQueryService;

    @Override
    public String handle(String rawMessage, EquipmentConfig source) {
        log.info("Recebida mensagem do equipamento [{}]. Protocolo: {}.", source.getName(), source.getProtocol());

        try {
            // Passo 1: Detectar se é uma mensagem de Query
            String sampleIdFromQuery = isQueryMessage(rawMessage, source.getProtocol());
            if (sampleIdFromQuery != null) {
                log.info("Mensagem identificada como uma QUERY para a amostra [{}].", sampleIdFromQuery);
                return hostQueryService.processQuery(sampleIdFromQuery, source);
            }

            // Passo 2: Se não for query, processar como mensagem de resultado
            log.info("Mensagem identificada como um RESULTADO. Iniciando processamento.");
            switch (source.getProtocol()) {
                case ASTM -> {
                    AstmMessage parsedAstmMessage = astmParserService.parse(rawMessage);
                    labOrderService.updateOrdersFromAstm(parsedAstmMessage);
                }
                case HL7 -> {
                    Hl7Message parsedHl7Message = hl7ParserService.parse(rawMessage, source.getParserHints());
                    if (parsedHl7Message != null) {
                        labOrderService.updateOrdersFromHl7(parsedHl7Message);
                    }
                }
                case ROCHE_HIF -> {
                    IntegraMessage parsedIntegraMessage = integraParserService.parse(rawMessage);
                    if (parsedIntegraMessage != null) {
                        labOrderService.updateOrdersFromIntegra(parsedIntegraMessage);
                    }
                }
                default -> log.warn("Protocolo desconhecido ou não suportado: {}. A mensagem não será processada.", source.getProtocol());
            }
        } catch (Exception e) {
            log.error("Falha crítica no dispatcher ao processar mensagem do equipamento [{}]. Erro: {}",
                    source.getName(), e.getMessage(), e);
        }

        return null;
    }

    // MÉTODO 'isQueryMessage' COM A LÓGICA FINAL E ROBUSTA
    private String isQueryMessage(String rawMessage, com.lab.api.domain.ProtocolType protocol) {
        if (rawMessage == null || rawMessage.isBlank()) return null;

        return switch (protocol) {
            case ASTM -> {
                // Divide a mensagem em registros individuais usando o Carriage Return (<CR>)
                String[] records = rawMessage.split("\r");
                for (String record : records) {
                    String trimmedRecord = record.trim();
                    // Verifica se um registro começa com "Q|" (Registro de Query)
                    if (trimmedRecord.startsWith("Q|")) {
                        try {
                            String[] fields = trimmedRecord.split("\\|");
                            // O campo 2 (índice 2) contém as informações do teste/amostra
                            String[] components = fields[2].split("\\^");
                            // O ID da amostra pode estar em diferentes sub-campos. Vamos procurar.
                            // Exemplo 1: Q|1|^SAMPLE123 -> components[1]
                            // Exemplo 2: Q|1|^^SAMPLE456 -> components[2]
                            for (int i = 1; i < components.length; i++) {
                                if (components[i] != null && !components[i].isBlank() && !"ALL".equalsIgnoreCase(components[i])) {
                                    yield components[i]; // Retorna o primeiro ID de amostra não vazio encontrado
                                }
                            }
                        } catch (Exception e) {
                            log.trace("Encontrado registro Q, mas falhou ao extrair ID de amostra.", e);
                            yield null;
                        }
                    }
                }
                yield null; // Nenhuma linha de Query encontrada
            }
            case HL7 -> {
                if (rawMessage.contains("MSH|") && rawMessage.contains("|TSREQ|") && rawMessage.contains("QPD|")) {
                    try {
                        String qpdLine = rawMessage.substring(rawMessage.indexOf("QPD|"));
                        String[] fields = qpdLine.split("\\|");
                        yield fields[3]; // Retorna o ID da amostra do campo QPD-3
                    } catch (Exception e) {
                        log.trace("Não foi possível extrair ID de amostra da query HL7.", e);
                        yield null;
                    }
                }
                yield null;
            }
            default -> null;
        };
    }
}