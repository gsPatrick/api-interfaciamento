package com.lab.api.parser.astm;

import com.lab.api.domain.LabOrder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class AstmMessageBuilder {

    private static final String CR = "\r";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String buildOrderMessage(String sampleId, List<LabOrder> orders) {
        if (orders.isEmpty()) {
            return ""; // Não há ordens para enviar
        }

        StringBuilder message = new StringBuilder();
        LabOrder firstOrder = orders.get(0); // Usado para dados do paciente

        // 1. Header Record
        message.append("H|\\^&|||||||||||P|LIS2-A").append(CR);

        // 2. Patient Record
        message.append("P|1|||").append(firstOrder.getPatientName() != null ? firstOrder.getPatientName() : "").append("||||||||||").append(CR);

        // 3. Order Records (um para cada exame)
        int orderSequence = 1;
        for (LabOrder order : orders) {
            String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            message.append("O|")
                    .append(orderSequence++)
                    .append("|").append(sampleId) // Specimen ID
                    .append("||^^^").append(order.getTestType()) // Universal Test ID
                    .append("||").append(timestamp)
                    .append("|||||||||F")
                    .append(CR);
        }

        // 4. Terminator Record
        message.append("L|1|N").append(CR);

        return message.toString();
    }
}