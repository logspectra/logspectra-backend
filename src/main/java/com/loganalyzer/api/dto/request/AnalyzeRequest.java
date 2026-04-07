package com.loganalyzer.api.dto.request;

import com.loganalyzer.ai.AIProvider;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeRequest {

    private String traceId;
    private String service;
    private List<String> logIds;
    private String query;
    private Instant from;
    private Instant to;
    private String provider;
    private String apiKey;
    private Map<String, Object> providerSettings;

    @AssertTrue(message = "traceId, logIds, or query with from/to must be provided")
    public boolean isValidScope() {
        return (traceId != null && !traceId.isBlank())
            || (logIds != null && !logIds.isEmpty())
            || ((query != null && !query.isBlank()) && from != null && to != null)
            || ((service != null && !service.isBlank()) && from != null && to != null);
    }
}
