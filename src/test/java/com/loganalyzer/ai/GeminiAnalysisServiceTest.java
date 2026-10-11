package com.loganalyzer.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeminiAnalysisServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizeModelNameStripsModelsPrefix() {
        assertEquals("gemini-flash-latest", GeminiAnalysisService.normalizeModelName("models/gemini-flash-latest"));
        assertEquals("gemini-2.0-flash", GeminiAnalysisService.normalizeModelName("gemini-2.0-flash"));
    }

    @Test
    void extractTextReadsCandidateParts() throws Exception {
        JsonNode root = objectMapper.readTree("""
            {
              "candidates": [{
                "content": {
                  "parts": [{ "text": "{\\"errorSummary\\":\\"x\\"}" }]
                }
              }]
            }
            """);

        assertEquals("{\"errorSummary\":\"x\"}", GeminiAnalysisService.extractText(root));
    }
}
