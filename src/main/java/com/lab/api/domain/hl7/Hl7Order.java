// src/main/java/com/lab/api/domain/hl7/Hl7Order.java
package com.lab.api.domain.hl7;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Hl7Order {
    // Corresponde ao campo OBR-2 ou OBR-3 da mensagem HL7
    private String specimenId;
    // Corresponde ao OBR-4
    private String universalServiceId;
    private String universalServiceText;
}