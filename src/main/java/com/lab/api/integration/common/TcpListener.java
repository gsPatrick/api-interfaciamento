// src/main/java/com/lab/api/integration/common/TcpListener.java
package com.lab.api.integration.common;

import com.lab.api.config.EquipmentConfig;
import com.lab.api.log.MessageAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpListener implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TcpListener.class);

    // Caracteres de controle do protocolo MLLP para HL7
    private static final byte VT = 0x0B; // Start of Block (Vertical Tab)
    private static final byte FS = 0x1C; // File Separator (End of Block)
    private static final byte CR = 0x0D; // Carriage Return

    private final EquipmentConfig config;
    private final MessageHandler messageHandler;
    private ServerSocket serverSocket;
    private volatile boolean running = true; // Usamos volatile para garantir visibilidade entre threads

    public TcpListener(EquipmentConfig config, MessageHandler messageHandler, MessageAuditService messageAuditService) {
        this.config = config;
        this.messageHandler = messageHandler;
    }

    @Override
    public void run() {
        int port = config.getCommunication().getPort();
        try {
            serverSocket = new ServerSocket(port);
            log.info("[{}] Servidor TCP iniciado. Aguardando conexões na porta {}.", config.getName(), port);

            while (running) {
                try {
                    // accept() é uma chamada bloqueante, espera até que um cliente se conecte
                    Socket clientSocket = serverSocket.accept();
                    log.info("[{}] Cliente conectado de: {}", config.getName(), clientSocket.getInetAddress().getHostAddress());

                    // Delega o tratamento da conexão para uma nova thread para não bloquear novas conexões
                    new Thread(new ClientHandler(clientSocket)).start();

                } catch (IOException e) {
                    if (running) {
                        log.error("[{}] Erro ao aceitar conexão de cliente.", config.getName(), e);
                    } else {
                        log.info("[{}] Servidor TCP na porta {} foi encerrado.", config.getName(), port);
                    }
                }
            }
        } catch (IOException e) {
            log.error("[{}] Não foi possível iniciar o servidor TCP na porta {}.", config.getName(), port, e);
        }
    }

    // Classe interna para lidar com cada cliente conectado
    private class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (InputStream input = clientSocket.getInputStream(); OutputStream output = clientSocket.getOutputStream()) {
                StringBuilder messageBuilder = new StringBuilder();
                int byteRead;
                boolean inMessage = false;

                while ((byteRead = input.read()) != -1) {
                    if (byteRead == VT) {
                        inMessage = true;
                        messageBuilder.setLength(0); // Inicia uma nova mensagem
                    } else if (byteRead == FS) {
                        // Verifica se o próximo caractere é o CR
                        if (input.read() == CR) {
                            inMessage = false;
                            String completeMessage = messageBuilder.toString();
                            log.info("[{}] Mensagem HL7 recebida completa.", config.getName());
                            messageHandler.handle(completeMessage, config);

                            // TODO: Enviar HL7 ACK de volta para o equipamento
                        }
                    } else if (inMessage) {
                        messageBuilder.append((char) byteRead);
                    }
                }
            } catch (IOException e) {
                log.warn("[{}] Conexão com o cliente {} perdida: {}", config.getName(), clientSocket.getInetAddress(), e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    log.info("[{}] Conexão com o cliente {} fechada.", config.getName(), clientSocket.getInetAddress());
                } catch (IOException e) {
                    log.error("[{}] Erro ao fechar o socket do cliente.", config.getName(), e);
                }
            }
        }
    }

    public void close() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("[{}] Erro ao fechar o ServerSocket.", config.getName(), e);
        }
    }
}