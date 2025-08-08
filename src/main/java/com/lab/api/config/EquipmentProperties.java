// src/main/java/com/lab/api/config/EquipmentProperties.java
package com.lab.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "equipments")
@Data
public class EquipmentProperties {
    private Map<String, EquipmentConfig> devices;
}