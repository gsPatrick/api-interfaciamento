// src/main/java/com/lab/api/domain/astm/ResultRecord.java
package com.lab.api.domain.astm;

import lombok.Data;

@Data
public class ResultRecord {
    private String sequenceNumber;
    private String universalTestId;
    private String value;
    private String units;
    private String referenceRange;
    private String resultAbnormalFlags;
    private String status;
}