// src/main/java/com/lab/api/domain/astm/AstmMessage.java
package com.lab.api.domain.astm;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AstmMessage {
    private HeaderRecord headerRecord;
    private PatientRecord patientRecord;
    private List<OrderRecord> orderRecords = new ArrayList<>();
    private List<ResultRecord> resultRecords = new ArrayList<>();
    private TerminatorRecord terminatorRecord;

    public void addOrderRecord(OrderRecord record) {
        this.orderRecords.add(record);
    }

    public void addResultRecord(ResultRecord record) {
        this.resultRecords.add(record);
    }
}