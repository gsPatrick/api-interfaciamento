package com.lab.api.controller;

import com.lab.api.domain.LabOrder;
import com.lab.api.service.LabOrderService;
import jakarta.validation.Valid; // Importar
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class LabOrderController {

    private final LabOrderService labOrderService;

    // Adicionamos @Valid para ativar a validação do DTO
    @PostMapping
    public ResponseEntity<OrderResponseDto> receiveOrder(@Valid @RequestBody OrderRequestDto orderRequest) {
        LabOrder createdOrder = labOrderService.createOrder(orderRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponseDto.fromEntity(createdOrder));
    }

    @GetMapping
    public ResponseEntity<OrderResponseDto> getOrderResult(
            @RequestParam String sampleId,
            @RequestParam String testType) {

        Optional<LabOrder> orderOpt = labOrderService.findBySampleIdAndTestType(sampleId, testType);

        return orderOpt
                .map(order -> ResponseEntity.ok(OrderResponseDto.fromEntity(order)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/all")
    public ResponseEntity<List<OrderResponseDto>> getAllOrders() {
        List<OrderResponseDto> allOrders = labOrderService.findAllOrders()
                .stream()
                .map(OrderResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(allOrders);
    }
}