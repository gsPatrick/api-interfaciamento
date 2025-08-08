package com.lab.api.integration.common;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.lab.api.config.CommunicationConfig;
import com.lab.api.config.EquipmentConfig;
import com.lab.api.log.MessageAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialListener implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SerialListener.class);

    // Caracteres de controle do protocolo ASTM
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte EOT = 0x04;
    private static final byte ENQ = 0x05;
    private static final byte ACK = 0x06;
    private static final byte CR = 0x0D;
    private static final byte LF = 0x0A;

    private final EquipmentConfig config;
    private final MessageHandler messageHandler;
    private final MessageAuditService messageAuditService;
    private SerialPort activePort;
    private final StringBuilder frameBuffer = new StringBuilder();

    public SerialListener(EquipmentConfig config, MessageHandler messageHandler, MessageAuditService messageAuditService) {
        this.config = config;
        this.messageHandler = messageHandler;
        this.messageAuditService = messageAuditService;
    }

    @Override
    public void run() {
        CommunicationConfig commConfig = config.getCommunication();
        activePort = SerialPort.getCommPort(commConfig.getPortName());

        activePort.setBaudRate(commConfig.getBaudRate());
        activePort.setNumDataBits(commConfig.getDataBits());
        activePort.setNumStopBits(commConfig.getStopBits());
        activePort.setParity(getParity(commConfig.getParity()));
        activePort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);

        if (activePort.openPort()) {
            log.info("[{}] Porta serial {} aberta com sucesso. Aguardando comunicação.", config.getName(), commConfig.getPortName());
            listen();
        } else {
            log.error("[{}] Falha ao abrir a porta serial {}.", config.getName(), commConfig.getPortName());
        }
    }

    private void listen() {
        activePort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                    return;
                }
                byte[] newData = new byte[activePort.bytesAvailable()];
                activePort.readBytes(newData, newData.length);
                for (byte b : newData) {
                    handleByte(b);
                }
            }
        });
    }

    private void handleByte(byte b) {
        switch (b) {
            case ENQ:
                log.info("[{}] -> ENQ recebido. Limpando buffer e enviando ACK...", config.getName());
                frameBuffer.setLength(0);
                sendAck();
                break;
            case STX:
                break;
            case EOT:
                log.info("[{}] -> EOT recebido. Fim da transmissão.", config.getName());
                if (frameBuffer.length() > 0) {
                    String rawMessage = frameBuffer.toString();
                    messageAuditService.auditMessage(rawMessage, config);
                    String response = messageHandler.handle(rawMessage, config);
                    if (response != null && !response.isEmpty()) {
                        sendResponse(response);
                    }
                    frameBuffer.setLength(0);
                }
                break;
            default:
                frameBuffer.append((char) b);
        }
    }

    private void sendAck() {
        if (activePort.isOpen()) {
            activePort.writeBytes(new byte[]{ACK}, 1);
            log.info("[{}] <- ACK enviado.", config.getName());
        }
    }

    private String calculateChecksum(byte[] frameData) {
        int sum = 0;
        for (byte b : frameData) {
            sum = (sum + b) & 0xFF;
        }
        return String.format("%02X", sum);
    }

    private void sendResponse(String responseMessage) {
        log.info("[{}] <- Enviando resposta da Query para o equipamento...", config.getName());
        try {
            activePort.writeBytes(new byte[]{ENQ}, 1);
            Thread.sleep(100);

            byte[] messageBytes = responseMessage.getBytes();
            byte[] frameContent = new byte[messageBytes.length + 2];
            frameContent[0] = '1';
            System.arraycopy(messageBytes, 0, frameContent, 1, messageBytes.length);
            frameContent[frameContent.length - 1] = ETX;

            String checksum = calculateChecksum(frameContent);

            byte[] header = new byte[]{STX};
            byte[] footer = new byte[]{CR, LF};

            activePort.writeBytes(header, header.length);
            activePort.writeBytes(frameContent, frameContent.length);
            activePort.writeBytes(checksum.getBytes(), checksum.length());
            activePort.writeBytes(footer, footer.length);

            Thread.sleep(100);

            activePort.writeBytes(new byte[]{EOT}, 1);

            log.info("[{}] <- Resposta enviada com sucesso (Checksum: {}).", config.getName(), checksum);
        } catch (Exception e) {
            log.error("[{}] Erro ao enviar resposta para o equipamento: {}", config.getName(), e.getMessage());
            Thread.currentThread().interrupt();
        }
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

    public void close() {
        if (activePort != null && activePort.isOpen()) {
            activePort.removeDataListener();
            activePort.closePort();
            log.info("[{}] Porta serial {} fechada.", config.getName(), config.getCommunication().getPortName());
        }
    }
}