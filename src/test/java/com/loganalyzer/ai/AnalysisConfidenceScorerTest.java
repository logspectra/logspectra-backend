package com.loganalyzer.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.SuggestedFix;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisConfidenceScorerTest {

    private final AnalysisConfidenceScorer scorer = new AnalysisConfidenceScorer();

    @Test
    void normalizeModelConfidence_acceptsZeroToOne() {
        assertEquals(0.72, AnalysisConfidenceScorer.normalizeModelConfidence(0.72), 0.001);
    }

    @Test
    void normalizeModelConfidence_convertsPercentScale() {
        assertEquals(0.85, AnalysisConfidenceScorer.normalizeModelConfidence(85), 0.001);
    }

    @Test
    void apply_replacesUntrustedLowModelValueWithEvidenceScore() {
        LogDocument log = LogDocument.builder()
            .level("ERROR")
            .exception("java.lang.NullPointerException")
            .stackTrace("at com.example.Service.run(Service.java:42)")
            .message("failed")
            .build();
        AnalysisResult result = AnalysisResult.builder()
            .confidence(0.01)
            .errorSummary("Service failed while handling checkout due to a null dependency.")
            .rootCause("CheckoutHandler invoked Service.run before initializing the payment client reference.")
            .severity("HIGH")
            .affectedServices(List.of("checkout"))
            .errorPattern("NullPointerException in Service.run")
            .suggestedFixes(List.of(SuggestedFix.builder()
                .fix("Initialize client")
                .filePath("src/main/java/com/example/Service.java")
                .search("client = null;")
                .replace("client = factory.create();")
                .build()))
            .build();

        scorer.apply(List.of(log, log, log), result);

        assertTrue(result.getConfidence() >= 0.55, "expected evidence-based score, got " + result.getConfidence());
    }

    @Test
    void apply_blendsHighModelConfidenceWithEvidence() {
        AnalysisResult result = AnalysisResult.builder()
            .confidence(0.8)
            .errorSummary("Short")
            .rootCause("Short")
            .severity("LOW")
            .build();

        scorer.apply(List.of(), result);

        assertTrue(result.getConfidence() >= 0.35 && result.getConfidence() <= 0.85);
    }
}
