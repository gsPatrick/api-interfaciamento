package com.lab.api.parser.integra;

import com.lab.api.domain.integra.IntegraMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@Slf4j
public class IntegraParserService {

    private static final char SOH = 0x01;
    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char EOT = 0x04;
    private static final char LF = 0x0A;

    public IntegraMessage parse(String rawBlock) {
        log.info("Iniciando parse da mensagem do INTEGRA 400/PLUS.");
        if (rawBlock == null || !rawBlock.startsWith(String.valueOf(SOH)) || !rawBlock.contains(String.valueOf(EOT))) {
            log.error("Bloco de mensagem inválido ou incompleto.");
            return null;
        }

        try {
            IntegraMessage message = new IntegraMessage();

            int stxIndex = rawBlock.indexOf(STX);
            int etxIndex = rawBlock.indexOf(ETX);

            // Extrai o Header (entre SOH e STX)
            message.setRawHeader(rawBlock.substring(1, stxIndex).trim());

            // Extrai as linhas de dados (entre STX e ETX)
            String dataContent = rawBlock.substring(stxIndex + 1, etxIndex).trim();
            message.setDataLines(Arrays.asList(dataContent.split(String.valueOf(LF))));

            // Extrai o Checksum (após ETX e antes de EOT)
            String remaining = rawBlock.substring(etxIndex + 1);
            String checksumPart = remaining.substring(0, remaining.indexOf(EOT)).trim();
            // O checksum pode vir com um número de linha, ex: "1\n873". Pegamos o último valor.
            String[] checksumLines = checksumPart.split("\n");
            message.setChecksum(checksumLines[checksumLines.length - 1].trim());

            log.info("Mensagem do INTEGRA parseada com sucesso. Header: [{}], Linhas de dados: {}", message.getRawHeader(), message.getDataLines().size());
            return message;

        } catch (Exception e) {
            log.error("Erro crítico ao fazer parse da mensagem do INTEGRA: {}", e.getMessage(), e);
            return null;
        }
    }
}