package com.loganalyzer.api.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeverityRuleResponse {
    private UUID id;
    private String ruleName;
    private String exceptionPattern;
    private String serviceName;
    private String severity;
    private Instant createdAt;
    private Instant updatedAt;
}

