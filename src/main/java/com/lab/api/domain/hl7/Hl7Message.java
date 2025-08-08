// src/main/java/com/lab/api/domain/hl7/Hl7Message.java
package com.lab.api.domain.hl7;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Hl7Message {
    private String messageControlId;
    private String sendingApplication;
    private Hl7Patient patient;
    private Hl7Order order; // <-- ADICIONADO!
    private final List<Hl7Result> results = new ArrayList<>();

    public void addResult(Hl7Result result) {
        this.results.add(result);
    }
}