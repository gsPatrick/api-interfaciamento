// src/main/java/com/lab/api/domain/OrderStatus.java
package com.lab.api.domain;

public enum OrderStatus {
    PENDING,    // Ordem recebida, aguardando resultado do equipamento
    PROCESSING, // Equipamento está processando
    COMPLETED,  // Resultado recebido e processado
    ERROR       // Ocorreu um erro
}