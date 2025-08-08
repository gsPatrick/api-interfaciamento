// src/main/java/com/lab/api/domain/LabOrder.java
package com.lab.api.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "lab_orders")
public class LabOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sampleId;
    private String patientName;
    private String testType; // Ex: "GLUCOSE", "TSH", etc.

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String resultValue;
    private String resultUnits;
}