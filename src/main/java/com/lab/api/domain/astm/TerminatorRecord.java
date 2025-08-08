// src/main/java/com/lab/api/domain/astm/TerminatorRecord.java
package com.lab.api.domain.astm;

import lombok.Data;

@Data
public class TerminatorRecord {
    private String sequenceNumber = "1";
    private String terminationCode = "F";
}