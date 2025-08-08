package com.lab.api.service;

import com.lab.api.controller.OrderRequestDto;
import com.lab.api.domain.LabOrder;
import com.lab.api.domain.OrderStatus;
import com.lab.api.domain.astm.AstmMessage;
import com.lab.api.domain.hl7.Hl7Message;
import com.lab.api.domain.integra.IntegraMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabOrderService {

    private final LabOrderRepository labOrderRepository;

    @Transactional
    public LabOrder createOrder(OrderRequestDto orderRequest) {
        log.info("Recebida nova ordem do LIS para amostra [{}], exame [{}]", orderRequest.getSampleId(), orderRequest.getTestType());
        LabOrder newOrder = new LabOrder();
        newOrder.setSampleId(orderRequest.getSampleId());
        newOrder.setPatientName(orderRequest.getPatientName());
        newOrder.setTestType(orderRequest.getTestType().toUpperCase());
        newOrder.setStatus(OrderStatus.PENDING);
        return labOrderRepository.save(newOrder);
    }

    @Transactional
    public void updateOrdersFromAstm(AstmMessage astmMessage) {
        if (astmMessage.getPatientRecord() == null || astmMessage.getResultRecords().isEmpty()) {
            log.warn("Mensagem ASTM recebida sem registros de paciente ou resultado. Ignorando.");
            return;
        }

        String sampleId = astmMessage.getOrderRecords().stream()
                .findFirst()
                .map(order -> order.getSpecimenId())
                .orElse(null);

        if (sampleId == null) {
            log.warn("Não foi possível determinar o Sample ID do registro de Ordem na mensagem ASTM. Ignorando.");
            return;
        }

        log.info("Processando resultados ASTM para a amostra ID: [{}]", sampleId);

        astmMessage.getResultRecords().forEach(result -> {
            String testType = result.getUniversalTestId().toUpperCase();
            Optional<LabOrder> orderOpt = labOrderRepository.findBySampleIdAndTestType(sampleId, testType);

            orderOpt.ifPresentOrElse(order -> {
                order.setResultValue(result.getValue());
                order.setResultUnits(result.getUnits());
                order.setStatus(OrderStatus.COMPLETED);
                labOrderRepository.save(order);
                log.info("SUCESSO: Ordem atualizada via ASTM. Amostra [{}], Teste [{}], Resultado [{}]", sampleId, testType, result.getValue());
            }, () -> {
                log.warn("NÃO ENCONTRADA: Ordem para Amostra [{}], Teste [{}] não encontrada no sistema.", sampleId, testType);
            });
        });
    }

    @Transactional
    public void updateOrdersFromHl7(Hl7Message hl7Message) {
        if (hl7Message.getPatient() == null || hl7Message.getResults().isEmpty()) {
            log.warn("Mensagem HL7 recebida sem dados do paciente ou resultados. Ignorando.");
            return;
        }

        String sampleId = hl7Message.getOrder().getSpecimenId();
        if (sampleId == null || sampleId.isBlank()) {
            log.warn("Não foi possível determinar o Sample ID na mensagem HL7. Ignorando.");
            return;
        }

        log.info("Processando resultados HL7 para a amostra ID: [{}]", sampleId);

        hl7Message.getResults().forEach(result -> {
            String testType = result.getTestId().toUpperCase();
            Optional<LabOrder> orderOpt = labOrderRepository.findBySampleIdAndTestType(sampleId, testType);

            orderOpt.ifPresentOrElse(order -> {
                order.setResultValue(result.getValue());
                order.setResultUnits(result.getUnits());
                order.setStatus(OrderStatus.COMPLETED);
                labOrderRepository.save(order);
                log.info("SUCESSO: Ordem atualizada via HL7. Amostra [{}], Teste [{}], Resultado [{}]", sampleId, testType, result.getValue());
            }, () -> {
                log.warn("NÃO ENCONTRADA: Ordem para Amostra [{}], Teste [{}] não encontrada no sistema.", sampleId, testType);
            });
        });
    }

    @Transactional
    public void updateOrdersFromIntegra(IntegraMessage integraMessage) {
        if (integraMessage == null || integraMessage.getDataLines().isEmpty()) {
            log.warn("Mensagem do Integra recebida vazia ou sem linhas de dados. Ignorando.");
            return;
        }

        String sampleId = null;
        String testType = null;
        String resultValue = null;
        String resultUnits = null;

        for (String line : integraMessage.getDataLines()) {
            String[] parts = line.split("_", 2);
            if (parts.length < 2) continue;
            String lineId = parts[0].trim();
            String lineValue = parts[1].trim();
            switch (lineId) {
                case "54":
                    sampleId = lineValue.trim();
                    break;
                case "55":
                    testType = lineValue.trim();
                    break;
                case "00":
                    String[] resultParts = lineValue.split("\\s+");
                    if (resultParts.length > 0) resultValue = resultParts[0];
                    if (resultParts.length > 1) resultUnits = resultParts[1];
                    break;
            }
        }

        if (sampleId != null && testType != null && resultValue != null) {
            log.info("Processando resultado do Integra para Amostra [{}], Teste [{}]", sampleId, testType);

            final String finalSampleId = sampleId;
            final String finalTestType = testType;
            final String finalResultValue = resultValue;
            final String finalResultUnits = resultUnits;

            Optional<LabOrder> orderOpt = findBySampleIdAndTestType(finalSampleId, finalTestType);

            orderOpt.ifPresentOrElse(
                    order -> {
                        order.setResultValue(finalResultValue);
                        order.setResultUnits(finalResultUnits);
                        order.setStatus(OrderStatus.COMPLETED);
                        labOrderRepository.save(order);
                        log.info("SUCESSO: Ordem atualizada via Integra. Amostra [{}], Teste [{}], Resultado [{}]",
                                order.getSampleId(), order.getTestType(), finalResultValue);
                    },
                    () -> {
                        log.warn("NÃO ENCONTRADA: Ordem para Amostra [{}], Teste [{}] não encontrada no sistema.",
                                finalSampleId, finalTestType);
                    }
            );
        } else {
            log.warn("Não foi possível extrair todas as informações necessárias da mensagem do Integra. Amostra: [{}], Teste: [{}], Resultado: [{}]",
                    sampleId, testType, resultValue);
        }
    }

    @Transactional
    public List<LabOrder> findPendingOrdersBySampleId(String sampleId) {
        log.info("Buscando ordens pendentes para a amostra [{}].", sampleId);
        List<LabOrder> allOrders = labOrderRepository.findAll();

        return allOrders.stream()
                .filter(order -> sampleId.equalsIgnoreCase(order.getSampleId()))
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .collect(Collectors.toList());
    }

    public Optional<LabOrder> findBySampleIdAndTestType(String sampleId, String testType) {
        return labOrderRepository.findBySampleIdAndTestType(sampleId.toUpperCase(), testType.toUpperCase());
    }

    public List<LabOrder> findAllOrders() {
        return labOrderRepository.findAll();
    }
}