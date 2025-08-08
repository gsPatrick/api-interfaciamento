package com.lab.api.integration.common;

import com.lab.api.config.EquipmentConfig;

@FunctionalInterface
public interface MessageHandler {
    // Retorna uma String de resposta, ou null se não houver resposta.
    String handle(String rawMessage, EquipmentConfig source);
}