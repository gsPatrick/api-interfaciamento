// src/main/java/com/lab/api/LabIntegrationApiApplication.java
package com.lab.api;

import com.lab.api.config.EquipmentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EquipmentProperties.class) // <-- ADICIONE ESTA LINHA
public class LabIntegrationApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabIntegrationApiApplication.class, args);
    }
}