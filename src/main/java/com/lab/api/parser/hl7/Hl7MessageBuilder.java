package com.lab.api.parser.hl7;

import com.lab.api.domain.LabOrder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class Hl7MessageBuilder {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong messageControlId = new AtomicLong(System.currentTimeMillis());

    public String buildOrderMessage(String sampleId, List<LabOrder> orders) {
        if (orders.isEmpty()) {
            return "";
        }

        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        long controlId = messageControlId.getAndIncrement();
        LabOrder firstOrder = orders.get(0);

        StringBuilder hl7Message = new StringBuilder();

        // MSH - Message Header
        hl7Message.append("MSH|^~\\&|LIS|LAB|").append(firstOrder.getTestType()).append("|Device|").append(timestamp).append("||OUL^R22|").append(controlId).append("|P|2.5\r");

        // PID - Patient Identification
        hl7Message.append("PID|1||").append(sampleId).append("||").append(firstOrder.getPatientName() != null ? firstOrder.getPatientName() : "").append("\r");

        // OBR - Observation Request (um para cada exame)
        int obrSequence = 1;
        for(LabOrder order : orders){
            hl7Message.append("OBR|").append(obrSequence++)
                    .append("||").append(order.getId()) // Placer Order Number
                    .append("|").append(order.getTestType()) // Universal Service ID
                    .append("|||||||||||||||||||").append(sampleId).append("\r");
        }

        return hl7Message.toString();
    }
}