package com.lab.api.controller;

import com.lab.api.domain.LabOrder;
import lombok.Data;

@Data
public class OrderResponseDto {
    private Long id;
    private String sampleId;
    private String patientName;
    private String testType;
    private String status;
    private String resultValue;
    private String resultUnits;

    // Construtor estático para facilitar a conversão da Entidade para DTO
    public static OrderResponseDto fromEntity(LabOrder order) {
        OrderResponseDto dto = new OrderResponseDto();
        dto.setId(order.getId());
        dto.setSampleId(order.getSampleId());
        dto.setPatientName(order.getPatientName());
        dto.setTestType(order.getTestType());
        dto.setStatus(order.getStatus().name());
        dto.setResultValue(order.getResultValue());
        dto.setResultUnits(order.getResultUnits());
        return dto;
    }
}