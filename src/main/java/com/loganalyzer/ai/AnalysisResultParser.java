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

    public AnalysisResult parse(String raw) {
        try {
            JsonNode node = objectMapper.readTree(extractJsonObject(raw));
            List<SuggestedFix> fixes = new ArrayList<>();
            JsonNode suggestedFixes = node.path("suggestedFixes");
            if (suggestedFixes.isArray()) {
                suggestedFixes.forEach(item -> fixes.add(SuggestedFix.builder()
                    .fix(item.path("fix").asText())
                    .priority(item.path("priority").asText())
                    .effort(item.path("effort").asText())
                    .filePath(textOrNull(item, "filePath"))
                    .search(textOrNull(item, "search"))
                    .replace(textOrNull(item, "replace"))
                    .newContent(textOrNull(item, "newContent"))
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
                .errorSummary(raw.substring(0, Math.min(500, raw.length())))
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

    static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int fence = trimmed.lastIndexOf("```");
            if (fence > 0) {
                trimmed = trimmed.substring(0, fence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static String textOrNull(JsonNode item, String field) {
        JsonNode node = item.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
