// src/main/java/com/lab/api/log/MessageAuditService.java
package com.lab.api.log;

import com.lab.api.config.EquipmentConfig;
import com.lab.api.domain.ProtocolType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class MessageAuditService {

    private static final String BASE_LOG_DIR = "message_logs";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss-SSSSSS");

    /**
     * Salva a mensagem bruta recebida de um equipamento em um arquivo de log específico.
     * A estrutura de pastas será: message_logs/[NomeEquipamento]/[Data]/[Hora]_message.[ext]
     *
     * @param rawMessage A mensagem completa recebida.
     * @param config As configurações do equipamento que enviou a mensagem.
     */
    public void auditMessage(String rawMessage, EquipmentConfig config) {
        try {
            // 1. Determina o caminho do diretório (ex: message_logs/Abbott_Architect_c8000/2025-08-07)
            Path directoryPath = getDirectoryPath(config.getName());

            // 2. Cria os diretórios se eles não existirem
            Files.createDirectories(directoryPath);

            // 3. Monta o caminho completo do arquivo (diretório + nome do arquivo)
            Path filePath = directoryPath.resolve(getFileName(config.getProtocol()));

            // 4. Escreve a mensagem no arquivo
            Files.writeString(filePath, rawMessage);

            log.info("Mensagem bruta do equipamento [{}] salva em: {}", config.getName(), filePath);

        } catch (IOException e) {
            log.error("Falha ao salvar a mensagem de auditoria para o equipamento [{}]. Erro: {}", config.getName(), e.getMessage(), e);
        }
    }

    private Path getDirectoryPath(String equipmentName) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        // Sanitiza o nome do equipamento para ser um nome de pasta válido
        String sanitizedEquipmentName = equipmentName.replaceAll("[^a-zA-Z0-9.-]", "_");
        return Paths.get(BASE_LOG_DIR, sanitizedEquipmentName, today);
    }

    private String getFileName(ProtocolType protocol) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String extension = protocol == ProtocolType.HL7 ? ".hl7" : ".astm";
        return String.format("%s_message%s", timestamp, extension);
    }
}