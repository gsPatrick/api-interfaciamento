package com.lab.api.domain.hl7;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Hl7Patient {
    private String patientId;
    private String lastName;
    private String firstName;
    private String birthDate;
}