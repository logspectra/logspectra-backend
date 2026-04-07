package com.loganalyzer.service;

import com.loganalyzer.ai.AIAnalysisExecution;
import com.loganalyzer.ai.AIAnalysisRequest;
import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.ai.AIProviderRegistry;
import com.loganalyzer.ai.AIStreamListener;
import com.loganalyzer.ai.DynamicAIAnalysisService;
import com.loganalyzer.api.dto.request.AnalyzeRequest;
import com.loganalyzer.api.dto.request.LogSearchRequest;
import com.loganalyzer.api.dto.response.AnalysisResultResponse;
import com.loganalyzer.api.mapper.LogMapper;
import com.loganalyzer.domain.model.AISettings;
import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.IssueGroup;
import com.loganalyzer.domain.repository.AnalysisHistoryRepository;
import com.loganalyzer.domain.repository.IssueGroupRepository;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogAnalysisService {

    private final LogElasticsearchRepository repository;
    private final DynamicAIAnalysisService aiAnalysisService;
    private final AIProviderRegistry providerRegistry;
    private final LogMapper logMapper;
    private final AISettingsService aiSettingsService;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final IssueGroupRepository issueGroupRepository;

    @Async("aiExecutor")
    public CompletableFuture<AnalysisResultResponse> analyze(AnalyzeRequest request,
                                                             UUID projectId,
                                                             AIStreamListener streamListener) {
        // Perform security validation within the async context
        long started = System.currentTimeMillis();
        List<LogDocument> logs = resolveLogs(request, projectId);
        if (logs.isEmpty()) {
            throw new ResourceNotFoundException("No logs matched the analysis criteria");
        }

        AIAnalysisRequest aiRequest = resolveAIRequest(request);
        AIAnalysisExecution execution = aiAnalysisService.analyze(logs, aiRequest, streamListener);
        AnalysisResult result = execution.getResult();

        String service = logs.getFirst().getService();
        String exceptionType = logs.stream().map(LogDocument::getException).filter(StringUtils::hasText).findFirst().orElse(null);
        upsertIssue(result, service, exceptionType, logs, projectId);
        var history = logMapper.toHistory(result, execution.getProviderUsed().name(), estimateTokens(logs), System.currentTimeMillis() - started, estimateCost(logs));
        history.setProjectId(projectId);
        analysisHistoryRepository.save(history);
        return CompletableFuture.completedFuture(logMapper.toResponse(result));
    }

    private AIAnalysisRequest resolveAIRequest(AnalyzeRequest request) {
        AIProvider requestedProvider = providerRegistry.resolve(request.getProvider());
        AISettings activeSettings = aiSettingsService.getActiveSettingsEntity();
        AIProvider effectiveProvider = requestedProvider != null ? requestedProvider : activeSettings.getProvider();

        AISettings providerSettings = aiSettingsService.findByProvider(effectiveProvider).orElse(activeSettings);
        java.util.Map<String, Object> runtimeSettings = new java.util.HashMap<>(aiSettingsService.toProviderSettings(providerSettings));
        if (request.getProviderSettings() != null) {
            runtimeSettings.putAll(request.getProviderSettings());
        }

        String runtimeApiKey = StringUtils.hasText(request.getApiKey()) ? request.getApiKey() : providerSettings.getApiKey();
        if (StringUtils.hasText(runtimeApiKey)) {
            runtimeSettings.put("apiKey", runtimeApiKey);
        }
        String runtimeModel = valueAsString(runtimeSettings.get("model"));
        String runtimeBaseUrl = valueAsString(runtimeSettings.get("baseUrl"));

        return AIAnalysisRequest.builder()
            .provider(effectiveProvider)
            .fallbackProvider(activeSettings.getProvider())
            .apiKey(runtimeApiKey)
            .model(runtimeModel)
            .baseUrl(runtimeBaseUrl)
            .openAiApiKey(runtimeApiKey)
            .providerSettings(runtimeSettings)
            .build();
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<LogDocument> resolveLogs(AnalyzeRequest request, UUID projectId) {
        try {
            if (StringUtils.hasText(request.getTraceId())) {
                return repository.fetchLogsForAnalysis(request.getTraceId(), projectId.toString());
            }
            List<LogDocument> logs = new ArrayList<>();
            if (request.getLogIds() != null && !request.getLogIds().isEmpty()) {
                LogSearchRequest searchRequest = new LogSearchRequest();
                searchRequest.setFrom(request.getFrom() != null ? request.getFrom() : Instant.now().minusSeconds(3600));
                searchRequest.setTo(request.getTo() != null ? request.getTo() : Instant.now());
                searchRequest.setSize(Math.max(request.getLogIds().size(), 20));
                searchRequest.setService(request.getService());
                var response = repository.search(searchRequest, projectId.toString());
                response.hits().hits().forEach(hit -> {
                    if (request.getLogIds().contains(hit.id()) && hit.source() != null) {
                        hit.source().setId(hit.id());
                        logs.add(hit.source());
                    }
                });
                return logs;
            }
            LogSearchRequest searchRequest = new LogSearchRequest();
            searchRequest.setQuery(request.getQuery());
            searchRequest.setService(request.getService());
            searchRequest.setFrom(request.getFrom() != null ? request.getFrom() : Instant.now().minusSeconds(3600));
            searchRequest.setTo(request.getTo() != null ? request.getTo() : Instant.now());
            searchRequest.setSize(200);
            var response = repository.search(searchRequest, projectId.toString());
            response.hits().hits().forEach(hit -> {
                if (hit.source() != null) {
                    hit.source().setId(hit.id());
                    logs.add(hit.source());
                }
            });
            return logs;
        } catch (IOException ex) {
            throw new com.loganalyzer.infrastructure.exception.ElasticsearchServiceException("Failed to load logs for analysis", ex);
        }
    }

    private void upsertIssue(AnalysisResult result, String service, String exceptionType, List<LogDocument> logs, UUID projectId) {
        Instant firstSeen = logs.stream().map(LogDocument::getTimestamp).filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(Instant.now());
        Instant lastSeen = logs.stream().map(LogDocument::getTimestamp).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(Instant.now());
        IssueGroup issue = issueGroupRepository.findByProjectIdAndFingerprint(projectId, result.getFingerprint())
            .orElse(IssueGroup.builder()
                .fingerprint(result.getFingerprint())
                .projectId(projectId)
                .service(service)
                .exceptionType(exceptionType)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .occurrenceCount(0L)
                .build());
        issue.setService(service);
        issue.setExceptionType(exceptionType);
        issue.setErrorSummary(result.getErrorSummary());
        issue.setRootCause(result.getRootCause());
        issue.setSeverity(result.getSeverity());
        issue.setLastSeen(lastSeen);
        issue.setOccurrenceCount(issue.getOccurrenceCount() + logs.size());
        issue.setSuggestedFixes(result.getSuggestedFixes());
        issueGroupRepository.save(issue);
    }

    private int estimateTokens(List<LogDocument> logs) {
        return logs.stream().mapToInt(log -> (log.getMessage() == null ? 0 : log.getMessage().length()) / 4 + 30).sum();
    }

    private double estimateCost(List<LogDocument> logs) {
        return estimateTokens(logs) * 0.00001d;
    }
}
