package com.lab.api.integration.common;

import com.lab.api.config.EquipmentConfig;
import java.util.Optional;

// NOVO: Interface para comunicação ativa.
public interface SerialCommunicator extends Runnable {
    void open();
    void close();
    Optional<String> sendRequestAndReceiveResponse(String requestMessage);
    boolean isPortOpen();
}