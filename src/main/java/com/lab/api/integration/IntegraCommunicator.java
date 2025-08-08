package com.lab.api.integration;

import com.fazecast.jSerialComm.SerialPort;
import com.lab.api.config.CommunicationConfig;
import com.lab.api.config.EquipmentConfig;
import com.lab.api.integration.common.SerialCommunicator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

@Slf4j
public class IntegraCommunicator implements SerialCommunicator {

    private final EquipmentConfig config;
    private SerialPort activePort;
    private static final byte EOT = 0x04;
    private static final int TIMEOUT_MS = 15000; // Timeout de 15s para receber a resposta

    public IntegraCommunicator(EquipmentConfig config) {
        this.config = config;
    }

    @Override
    public void open() {
        if (isPortOpen()) {
            log.info("[{}] A porta {} já está aberta.", config.getName(), config.getCommunication().getPortName());
            return;
        }
        CommunicationConfig commConfig = config.getCommunication();
        activePort = SerialPort.getCommPort(commConfig.getPortName());
        activePort.setBaudRate(commConfig.getBaudRate());
        activePort.setNumDataBits(commConfig.getDataBits());
        activePort.setNumStopBits(commConfig.getStopBits());
        activePort.setParity(getParity(commConfig.getParity()));

        if (activePort.openPort()) {
            log.info("[{}] Porta serial {} aberta com sucesso para comunicação ativa.", config.getName(), commConfig.getPortName());
        } else {
            log.error("[{}] Falha ao abrir a porta serial {}.", config.getName(), commConfig.getPortName());
        }
    }

    @Override
    public Optional<String> sendRequestAndReceiveResponse(String requestMessage) {
        if (!isPortOpen()) {
            log.error("[{}] A porta não está aberta. Não é possível enviar a requisição.", config.getName());
            return Optional.empty();
        }

        try (InputStream in = activePort.getInputStream(); OutputStream out = activePort.getOutputStream()) {
            activePort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, TIMEOUT_MS, 0);

            // Envia a requisição
            out.write(requestMessage.getBytes());
            out.flush();
            log.info("[{}] -> Requisição enviada: {}", config.getName(), requestMessage.replace("\n", " ").replace("\r", ""));

            // Lê a resposta
            StringBuilder responseBuilder = new StringBuilder();
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                if(in.available() > 0) {
                    int byteRead = in.read();
                    if (byteRead == -1) break; // Fim do stream

                    responseBuilder.append((char) byteRead);
                    if (byteRead == EOT) { // Fim da transmissão
                        log.info("[{}] <- Resposta recebida completa.", config.getName());
                        return Optional.of(responseBuilder.toString());
                    }
                }
                // Pequena pausa para não sobrecarregar a CPU
                Thread.sleep(20);
            }
            log.warn("[{}] Timeout ao esperar resposta do equipamento.", config.getName());
            return Optional.empty();

        } catch (IOException | InterruptedException e) {
            log.error("[{}] Erro de I/O ou Interrupção durante a comunicação: {}", config.getName(), e.getMessage());
            Thread.currentThread().interrupt(); // Restaura o status de interrupção
            return Optional.empty();
        }
    }

    @Override
    public void run() {
        // Para comunicadores ativos, a thread apenas abre a porta e a mantém.
        // A comunicação é disparada por eventos externos (chamada de API).
        open();
    }

    @Override
    public void close() {
        if (isPortOpen()) {
            activePort.closePort();
            log.info("[{}] Porta serial {} fechada.", config.getName(), config.getCommunication().getPortName());
        }
    }

    @Override
    public boolean isPortOpen() {
        return activePort != null && activePort.isOpen();
    }

    private int getParity(String parityStr) {
        if (parityStr == null) return SerialPort.NO_PARITY;
        return switch (parityStr.toUpperCase()) {
            case "EVEN" -> SerialPort.EVEN_PARITY;
            case "ODD" -> SerialPort.ODD_PARITY;
            case "MARK" -> SerialPort.MARK_PARITY;
            case "SPACE" -> SerialPort.SPACE_PARITY;
            default -> SerialPort.NO_PARITY;
        };
    }
}