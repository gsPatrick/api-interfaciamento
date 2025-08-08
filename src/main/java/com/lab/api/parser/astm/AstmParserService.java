// src/main/java/com/lab/api/parser/astm/AstmParserService.java
package com.lab.api.parser.astm;

import com.lab.api.domain.astm.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AstmParserService {

    public AstmMessage parse(String rawMessage) {
        log.info("Iniciando parse da mensagem ASTM...");
        AstmMessage astmMessage = new AstmMessage();

        // O delimitador padrão de registros ASTM é o Carriage Return (CR, \r)
        String[] records = rawMessage.split("\r");

        for (String record : records) {
            // Ignora linhas vazias que podem vir do split
            if (record == null || record.trim().isEmpty()) {
                continue;
            }

            // O delimitador de campos é o pipe (|)
            // Usamos "\\|" para escapar o caractere especial |
            String[] fields = record.split("\\|");
            if (fields.length == 0) {
                continue;
            }

            String recordType = fields[0];

            try {
                // O primeiro caractere identifica o tipo de registro
                switch (recordType.charAt(0)) {
                    case 'H':
                        astmMessage.setHeaderRecord(parseHeader(fields));
                        break;
                    case 'P':
                        astmMessage.setPatientRecord(parsePatient(fields));
                        break;

                    case 'O':
                        astmMessage.addOrderRecord(parseOrder(fields));
                        break;
                    case 'R':
                        astmMessage.addResultRecord(parseResult(fields));
                        break;
                    case 'L':
                        astmMessage.setTerminatorRecord(parseTerminator(fields));
                        break;
                    default:
                        log.trace("Ignorando tipo de registro ASTM não suportado: {}", recordType);
                }
            } catch (Exception e) {
                log.error("Erro ao fazer o parse do registro ASTM: [{}]. Erro: {}", record, e.getMessage(), e);
            }
        }
        log.info("Parse da mensagem ASTM concluído. Paciente: {}, Ordens: {}, Resultados: {}",
                astmMessage.getPatientRecord() != null,
                astmMessage.getOrderRecords().size(),
                astmMessage.getResultRecords().size());
        return astmMessage;
    }

    private HeaderRecord parseHeader(String[] fields) {
        return new HeaderRecord(getField(fields, 4));
    }

    private PatientRecord parsePatient(String[] fields) {
        PatientRecord patient = new PatientRecord();
        patient.setSequenceNumber(getField(fields, 1));
        patient.setLaboratoryPatientId(getField(fields, 3));
        patient.setPatientName(getField(fields, 5));
        return patient;
    }

    private OrderRecord parseOrder(String[] fields) {
        OrderRecord order = new OrderRecord();
        order.setSequenceNumber(getField(fields, 1));
        order.setSpecimenId(getField(fields, 2)); // ID da Amostra (Ex: SAMPLE123)

        // Exemplo: ^^^GLUCOSE
        String[] testFields = getField(fields, 4).split("\\^");
        order.setUniversalTestId(getField(testFields, 3));
        return order;
    }

    private ResultRecord parseResult(String[] fields) {
        ResultRecord result = new ResultRecord();
        result.setSequenceNumber(getField(fields, 1));

        // Exemplo: ^^^GLUCOSE
        String[] testFields = getField(fields, 2).split("\\^");
        result.setUniversalTestId(getField(testFields, 3));

        result.setValue(getField(fields, 3));
        result.setUnits(getField(fields, 4));
        result.setReferenceRange(getField(fields, 5));
        result.setResultAbnormalFlags(getField(fields, 6));
        result.setStatus(getField(fields, 8));
        return result;
    }

    private TerminatorRecord parseTerminator(String[] fields) {
        TerminatorRecord terminator = new TerminatorRecord();
        terminator.setSequenceNumber(getField(fields, 1));
        terminator.setTerminationCode(getField(fields, 2));
        return terminator;
    }

    private String getField(String[] fields, int index) {
        if (fields != null && index < fields.length) {
            return fields[index].trim();
        }
        return "";
    }
}