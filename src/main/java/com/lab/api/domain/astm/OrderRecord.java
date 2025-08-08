// src/main/java/com/lab/api/domain/astm/OrderRecord.java
package com.lab.api.domain.astm;

import lombok.Data;

@Data
public class OrderRecord {
    private String sequenceNumber;
    private String specimenId; // Este é o ID da amostra que usamos para busca!
    private String universalTestId;
}