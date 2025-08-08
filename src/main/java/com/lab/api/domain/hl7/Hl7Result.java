package com.lab.api.domain.hl7;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Hl7Result {
    private String testId;
    private String testName;
    private String value;
    private String units;
    private String referenceRange;
    private String abnormalFlags;
    private String observationDateTime;
}