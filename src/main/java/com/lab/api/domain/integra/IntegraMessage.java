package com.lab.api.domain.integra;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class IntegraMessage {
    private String rawHeader;
    private List<String> dataLines = new ArrayList<>();
    private String checksum;
}