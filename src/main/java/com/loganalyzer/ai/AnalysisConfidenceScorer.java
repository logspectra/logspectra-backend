package com.loganalyzer.ai;

import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.SuggestedFix;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Produces a 0.0–1.0 confidence score by blending normalized model output with log/evidence signals.
 * Stored value is always on the 0–1 scale (UI multiplies by 100 for display).
 */
@Component
public class AnalysisConfidenceScorer {

    private static final double LOW_MODEL_TRUST_THRESHOLD = 0.06;

    public void apply(List<LogDocument> logs, AnalysisResult result) {
        if (result == null) {
            return;
        }
        double model = normalizeModelConfidence(result.getConfidence());
        double evidence = evidenceScore(logs, result);
        double blended = model <= LOW_MODEL_TRUST_THRESHOLD
            ? evidence
            : clamp(0.4 * model + 0.6 * evidence, 0.0, 1.0);
        result.setConfidence(roundTwoDecimals(blended));
    }

    static double normalizeModelConfidence(double raw) {
        if (Double.isNaN(raw) || raw < 0) {
            return 0.0;
        }
        if (raw > 1.0 && raw <= 100.0) {
            return clamp(raw / 100.0, 0.0, 1.0);
        }
        if (raw > 100.0) {
            return 1.0;
        }
        return clamp(raw, 0.0, 1.0);
    }

    static double evidenceScore(List<LogDocument> logs, AnalysisResult result) {
        double score = 0.25;

        List<LogDocument> safeLogs = logs == null ? List.of() : logs;
        long errorLines = safeLogs.stream()
            .filter(l -> l != null && "ERROR".equalsIgnoreCase(l.getLevel()))
            .count();
        boolean hasException = safeLogs.stream().anyMatch(l -> l != null && StringUtils.hasText(l.getException()));
        boolean hasStack = safeLogs.stream().anyMatch(l -> l != null && StringUtils.hasText(l.getStackTrace()));
        if (errorLines > 0) {
            score += 0.08;
        }
        if (errorLines >= 3) {
            score += 0.07;
        }
        if (hasException) {
            score += 0.1;
        }
        if (hasStack) {
            score += 0.12;
        }

        if (StringUtils.hasText(result.getErrorSummary()) && result.getErrorSummary().length() >= 40) {
            score += 0.08;
        }
        if (StringUtils.hasText(result.getRootCause()) && result.getRootCause().length() >= 60) {
            score += 0.12;
        }
        if (result.getAffectedServices() != null && !result.getAffectedServices().isEmpty()) {
            score += 0.05;
        }
        if (StringUtils.hasText(result.getErrorPattern())) {
            score += 0.05;
        }

        List<SuggestedFix> fixes = result.getSuggestedFixes() == null ? List.of() : result.getSuggestedFixes();
        if (!fixes.isEmpty()) {
            score += 0.06;
        }
        if (fixes.stream().anyMatch(AnalysisConfidenceScorer::isPatchFix)) {
            score += 0.14;
        }

        if ("CRITICAL".equalsIgnoreCase(result.getSeverity()) || "HIGH".equalsIgnoreCase(result.getSeverity())) {
            score += 0.04;
        }

        return clamp(score, 0.2, 0.95);
    }

    private static boolean isPatchFix(SuggestedFix fix) {
        return fix != null
            && StringUtils.hasText(fix.getFilePath())
            && StringUtils.hasText(fix.getSearch())
            && fix.getReplace() != null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
