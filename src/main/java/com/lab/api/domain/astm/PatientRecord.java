// src/main/java/com/lab/api/domain/astm/PatientRecord.java
package com.lab.api.domain.astm;

import lombok.Data;

@Data
public class PatientRecord {
    private String sequenceNumber;
    private String laboratoryPatientId;
    private String patientName;
}