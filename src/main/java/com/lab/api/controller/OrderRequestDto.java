package com.lab.api.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderRequestDto {

    @NotBlank(message = "O campo 'sampleId' não pode ser nulo ou vazio.")
    private String sampleId;

    private String patientName; // Pode ser opcional

    @NotBlank(message = "O campo 'testType' não pode ser nulo ou vazio.")
    private String testType;
}