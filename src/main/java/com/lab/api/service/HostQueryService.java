package com.lab.api.service;

import com.lab.api.config.EquipmentConfig;
import com.lab.api.domain.LabOrder;
import com.lab.api.parser.astm.AstmMessageBuilder;
import com.lab.api.parser.hl7.Hl7MessageBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HostQueryService {

    private final LabOrderService labOrderService;
    private final AstmMessageBuilder astmBuilder;
    private final Hl7MessageBuilder hl7Builder;

    /**
     * Processa uma requisição de ordem (query) de um equipamento.
     * @param sampleId O ID da amostra consultada.
     * @param config A configuração do equipamento que fez a consulta.
     * @return Uma string com a mensagem de resposta (ASTM ou HL7), ou null se não houver ordens.
     */
    public String processQuery(String sampleId, EquipmentConfig config) {
        List<LabOrder> pendingOrders = labOrderService.findPendingOrdersBySampleId(sampleId);

        if (pendingOrders.isEmpty()) {
            return null; // Indica ao listener que não há nada para responder.
        }

        return switch (config.getProtocol()) {
            case ASTM -> astmBuilder.buildOrderMessage(sampleId, pendingOrders);
            case HL7 -> hl7Builder.buildOrderMessage(sampleId, pendingOrders);
            default -> null;
        };
    }
}