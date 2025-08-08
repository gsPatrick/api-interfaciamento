package com.lab.api.config;

import com.lab.api.domain.ProtocolType;
import lombok.Data;
import java.util.Map; // NOVO

@Data
public class EquipmentConfig {
    private String name;
    private boolean enabled;
    private ProtocolType protocol;
    private com.lab.api.config.CommunicationConfig communication;

    // NOVO: "Dicas" para o parser saber onde encontrar informações específicas.
    private Map<String, String> parserHints;
}