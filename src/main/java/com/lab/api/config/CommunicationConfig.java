// src/main/java/com/lab/api/config/CommunicationConfig.java
package com.lab.api.config;

import com.lab.api.domain.CommunicationType;
import lombok.Data;

@Data // Gera getters, setters, toString, etc.
public class CommunicationConfig {
    private CommunicationType type;
    private String host;
    private int port;
    private String portName;
    private int baudRate;
    private int dataBits;
    private int stopBits;
    private String parity;
}