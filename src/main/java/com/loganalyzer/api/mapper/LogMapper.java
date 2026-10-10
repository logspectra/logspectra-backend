package com.loganalyzer.api.mapper;

import com.loganalyzer.api.dto.response.AnalysisResultResponse;
import com.loganalyzer.api.dto.response.IssueGroupResponse;
import com.loganalyzer.api.dto.response.LogSearchResponse;
import com.loganalyzer.api.dto.response.SuggestedFixResponse;
import com.loganalyzer.domain.model.AnalysisHistory;
import com.loganalyzer.domain.model.IssueGroup;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LogMapper {

    public LogSearchResponse toResponse(LogDocument document) {
        return toResponse(document, null);
    }

    public LogSearchResponse toResponse(LogDocument document, String severity) {
        return LogSearchResponse.builder()
            .id(document.getId())
            .timestamp(document.getTimestamp())
            .level(document.getLevel())
            .severity(severity)
            .service(document.getService())
            .traceId(document.getTraceId())
            .spanId(document.getSpanId())
            .thread(document.getThread())
            .logger(document.getLogger())
            .message(document.getMessage())
            .exception(document.getException())
            .stackTrace(document.getStackTrace())
            .host(document.getHost())
            .environment(document.getEnvironment())
            .resolved(document.getResolved() != null ? document.getResolved() : false)
            .build();
    }

    public AnalysisResultResponse toResponse(com.loganalyzer.domain.model.AnalysisResult result) {
        return AnalysisResultResponse.builder()
            .fingerprint(result.getFingerprint())
            .errorSummary(result.getErrorSummary())
            .rootCause(result.getRootCause())
            .service(result.getAffectedServices() != null && !result.getAffectedServices().isEmpty() ? result.getAffectedServices().get(0) : null)
            .affectedServices(result.getAffectedServices())
            .errorPattern(result.getErrorPattern())
            .severity(result.getSeverity())
            .suggestedFixes(mapSuggestedFixes(result.getSuggestedFixes()))
            .relatedTraceIds(result.getRelatedTraceIds())
            .confidence(result.getConfidence())
            .analyzedAt(result.getAnalyzedAt())
            .cachedResult(result.isCachedResult())
            .cached(result.isCachedResult())
            .build();
    }

    public IssueGroupResponse toResponse(IssueGroup issueGroup) {
        return IssueGroupResponse.builder()
            .id(issueGroup.getId() == null ? null : issueGroup.getId().toString())
            .severity(issueGroup.getSeverity())
            .title(issueGroup.getErrorSummary() != null ? issueGroup.getErrorSummary() : issueGroup.getExceptionType())
            .service(issueGroup.getService())
            .errorType(issueGroup.getExceptionType())
            .exceptionType(issueGroup.getExceptionType())
            .occurrenceCount(issueGroup.getOccurrenceCount())
            .occurrences(issueGroup.getOccurrenceCount())
            .firstSeen(issueGroup.getFirstSeen())
            .lastSeen(issueGroup.getLastSeen())
            .affectedServices(java.util.List.of(issueGroup.getService()))
            .trend(java.util.List.of(1, 2, 3, 4, 3, 2, 1))
            .build();
    }

    public AnalysisHistory toHistory(com.loganalyzer.domain.model.AnalysisResult result, String modelUsed, int tokensUsed, long durationMs, double costUsd) {
        return AnalysisHistory.builder()
            .fingerprint(result.getFingerprint())
            .modelUsed(modelUsed)
            .tokensUsed(tokensUsed)
            .durationMs((int) durationMs)
            .costUsd(java.math.BigDecimal.valueOf(costUsd))
            .cachedResult(result.isCachedResult())
            .build();
    }

    private List<SuggestedFixResponse> mapSuggestedFixes(List<com.loganalyzer.domain.model.SuggestedFix> fixes) {
        return fixes == null ? List.of() : fixes.stream()
            .map(fix -> SuggestedFixResponse.builder()
                .fix(fix.getFix())
                .priority(fix.getPriority())
                .effort(fix.getEffort())
                .filePath(fix.getFilePath())
                .build())
            .toList();
    }
}
