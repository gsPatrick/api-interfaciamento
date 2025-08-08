package com.lab.api.integration;

import com.lab.api.config.EquipmentConfig;
import com.lab.api.config.EquipmentProperties;
import com.lab.api.domain.ProtocolType;
import com.lab.api.integration.common.MessageHandler;
import com.lab.api.integration.common.SerialCommunicator;
import com.lab.api.integration.common.SerialListener;
import com.lab.api.integration.common.TcpListener;
import com.lab.api.log.MessageAuditService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EquipmentListenerManager {

    private final EquipmentProperties equipmentProperties;
    private final MessageAuditService messageAuditService;

    @Qualifier("protocolDispatcher")
    private final MessageHandler messageHandler;

    private final Map<String, Runnable> activeListeners = new HashMap<>();
    private final Map<String, Thread> activeThreads = new HashMap<>();

    @PostConstruct
    public void initializeListeners() {
        log.info("Iniciando gerenciador de listeners de equipamentos...");
        Map<String, EquipmentConfig> devices = equipmentProperties.getDevices();

        if (devices == null || devices.isEmpty()) {
            log.warn("Nenhum equipamento configurado em application.yml. Nenhum listener será iniciado.");
            return;
        }

        devices.forEach((id, config) -> {
            if (config.isEnabled()) {
                log.info("Iniciando listener para o equipamento: {} (ID: {})", config.getName(), id);
                startListenerFor(id, config);
            } else {
                log.info("Equipamento {} (ID: {}) está desabilitado na configuração.", config.getName(), id);
            }
        });
    }

    private void startListenerFor(String id, EquipmentConfig config) {
        Thread listenerThread;
        Runnable listener = null;

        // Lógica para comunicadores ativos (mestre-escravo)
        if (config.getProtocol() == ProtocolType.ROCHE_HIF) {
            log.info("--> Configurado para comunicação ATIVA (Mestre) com o protocolo ROCHE_HIF.");
            listener = new IntegraCommunicator(config);
        } else {
            // Lógica para listeners passivos (escuta)
            switch (config.getCommunication().getType()) {
                case TCP -> {
                    log.info("--> Configurado para comunicação TCP na porta {}", config.getCommunication().getPort());
                    listener = new TcpListener(config, messageHandler, messageAuditService);
                }
                case SERIAL -> {
                    log.info("--> Configurado para comunicação SERIAL na porta {}", config.getCommunication().getPortName());
                    listener = new SerialListener(config, messageHandler, messageAuditService);
                }
                default -> log.warn("Tipo de comunicação desconhecido para o equipamento: {}", config.getName());
            }
        }

        if (listener != null) {
            listenerThread = new Thread(listener);
            listenerThread.setName("listener-" + id);
            listenerThread.start();

            activeListeners.put(id, listener);
            activeThreads.put(id, listenerThread);
        }
    }

    // Método para enviar requisições para comunicadores ativos
    public Optional<String> sendRequest(String equipmentId, String request) {
        Runnable listener = activeListeners.get(equipmentId);
        if (listener instanceof SerialCommunicator communicator) {
            if (!communicator.isPortOpen()) {
                log.warn("A porta para o equipamento {} não está aberta. Tentando abrir...", equipmentId);
                communicator.open();
            }
            return communicator.sendRequestAndReceiveResponse(request);
        }
        log.error("O equipamento {} não suporta ou não foi configurado para comunicação ativa.", equipmentId);
        return Optional.empty();
    }

    // Método para obter a configuração de um equipamento específico
    public EquipmentConfig getEquipmentConfig(String equipmentId) {
        if (equipmentProperties.getDevices() == null) {
            return null;
        }
        return equipmentProperties.getDevices().get(equipmentId);
    }

    @PreDestroy
    public void shutdownListeners() {
        log.info("Encerrando todos os listeners de equipamentos...");
        activeListeners.forEach((id, listener) -> {
            if (listener instanceof SerialListener serialListener) {
                serialListener.close();
            } else if (listener instanceof TcpListener tcpListener) {
                tcpListener.close();
            } else if (listener instanceof SerialCommunicator communicator) {
                communicator.close();
            }
        });
        activeThreads.values().forEach(thread -> {
            try {
                thread.interrupt();
                thread.join(2000); // Espera a thread terminar
            } catch (InterruptedException e) {
                log.warn("Thread de listener foi interrompida durante o shutdown.");
                Thread.currentThread().interrupt();
            }
        });
        log.info("Todos os listeners foram encerrados.");
    }
}