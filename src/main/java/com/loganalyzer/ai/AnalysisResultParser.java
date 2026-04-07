package com.loganalyzer.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.SuggestedFix;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalysisResultParser {

    private final ObjectMapper objectMapper;

    public AnalysisResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisResult parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            List<SuggestedFix> fixes = new ArrayList<>();
            JsonNode suggestedFixes = node.path("suggestedFixes");
            if (suggestedFixes.isArray()) {
                suggestedFixes.forEach(item -> fixes.add(SuggestedFix.builder()
                    .fix(item.path("fix").asText())
                    .priority(item.path("priority").asText())
                    .effort(item.path("effort").asText())
                    .build()));
            }
            return AnalysisResult.builder()
                .errorSummary(node.path("errorSummary").asText())
                .rootCause(node.path("rootCause").asText())
                .affectedServices(readArray(node.path("affectedServices")))
                .errorPattern(node.path("errorPattern").asText())
                .severity(node.path("severity").asText("MEDIUM"))
                .suggestedFixes(fixes)
                .relatedTraceIds(readArray(node.path("relatedTraceIds")))
                .confidence(node.path("confidence").asDouble(0.0))
                .build();
        } catch (JsonProcessingException ex) {
            return AnalysisResult.builder()
                .errorSummary(json.substring(0, Math.min(500, json.length())))
                .rootCause("Unable to parse structured AI output")
                .severity("LOW")
                .confidence(0.0)
                .build();
        }
    }

    private List<String> readArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }
}
