package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.loganalyzer.ai.AIAnalysisExecution;
import com.loganalyzer.ai.AIAnalysisRequest;
import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.ai.AIProviderRegistry;
import com.loganalyzer.ai.AIStreamListener;
import com.loganalyzer.ai.DynamicAIAnalysisService;
import com.loganalyzer.api.dto.request.AnalyzeRequest;
import com.loganalyzer.api.dto.response.AnalysisResultResponse;
import com.loganalyzer.api.mapper.LogMapper;
import com.loganalyzer.domain.model.AISettings;
import com.loganalyzer.domain.model.AnalysisHistory;
import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.IssueGroup;
import com.loganalyzer.domain.repository.AnalysisHistoryRepository;
import com.loganalyzer.domain.repository.IssueGroupRepository;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import com.loganalyzer.github.RemediationPullRequestService;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogAnalysisServiceTest {

    @Mock
    private LogElasticsearchRepository repository;

    @Mock
    private DynamicAIAnalysisService aiAnalysisService;

    @Mock
    private AIProviderRegistry providerRegistry;

    @Mock
    private LogMapper logMapper;

    @Mock
    private AISettingsService aiSettingsService;

    @Mock
    private AnalysisHistoryRepository analysisHistoryRepository;

    @Mock
    private IssueGroupRepository issueGroupRepository;

    @Mock
    private RemediationPullRequestService remediationPullRequestService;

    @InjectMocks
    private LogAnalysisService logAnalysisService;

    private UUID projectId;
    private UUID userId;
    private AnalyzeRequest analyzeRequest;
    private List<LogDocument> mockLogs;
    private AnalysisResult mockAnalysisResult;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        analyzeRequest = AnalyzeRequest.builder()
            .traceId("trace-123")
            .provider(AIProvider.OLLAMA.name())
            .build();

        LogDocument log = LogDocument.builder()
            .id("log-1")
            .projectId(projectId.toString())
            .service("auth-service")
            .level("ERROR")
            .message("Authentication failed")
            .exception("AuthenticationException")
            .timestamp(Instant.now())
            .build();
        mockLogs = List.of(log);

        mockAnalysisResult = AnalysisResult.builder()
            .fingerprint("fp-123")
            .errorSummary("User authentication failed due to invalid credentials")
            .rootCause("Credential verification mismatch")
            .severity("HIGH")
            .suggestedFixes(List.of())
            .build();
    }

    @Test
    void analyzeShouldThrowWhenNoLogsFound() throws IOException {
        when(repository.fetchLogsForAnalysis(anyString(), anyString())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () ->
            logAnalysisService.analyze(analyzeRequest, projectId, mock(AIStreamListener.class))
        );
    }

    @Test
    void analyzeShouldReturnAnalysisResultWhenLogsExist() throws Exception {
        AIStreamListener streamListener = mock(AIStreamListener.class);
        AISettings mockSettings = AISettings.builder()
            .provider(AIProvider.OLLAMA)
            .build();

        AIAnalysisExecution execution = AIAnalysisExecution.builder()
            .result(mockAnalysisResult)
            .providerUsed(AIProvider.OLLAMA)
            .build();

        AnalysisResultResponse expectedResponse = AnalysisResultResponse.builder()
            .errorSummary(mockAnalysisResult.getErrorSummary())
            .rootCause(mockAnalysisResult.getRootCause())
            .severity(mockAnalysisResult.getSeverity())
            .build();

        when(repository.fetchLogsForAnalysis("trace-123", projectId.toString()))
            .thenReturn(mockLogs);
        when(aiSettingsService.getActiveSettingsEntity())
            .thenReturn(mockSettings);
        when(aiAnalysisService.analyze(any(), any(), any()))
            .thenReturn(execution);
        when(logMapper.toResponse(mockAnalysisResult))
            .thenReturn(expectedResponse);
        when(logMapper.toHistory(any(), anyString(), anyInt(), anyLong(), anyDouble()))
            .thenReturn(AnalysisHistory.builder().fingerprint("fp-123").build());
        when(issueGroupRepository.findByProjectIdAndFingerprint(projectId, "fp-123"))
            .thenReturn(Optional.empty());
        when(remediationPullRequestService.shouldCreate(false)).thenReturn(false);

        CompletableFuture<AnalysisResultResponse> result = logAnalysisService.analyze(analyzeRequest, projectId, streamListener);

        assertNotNull(result);
        assertEquals(expectedResponse, result.get());
        verify(issueGroupRepository).save(any(IssueGroup.class));
        verify(analysisHistoryRepository).save(any());
    }

    @Test
    void analyzeShouldUpsertIssueGroupOnNewFingerprint() throws Exception {
        AIStreamListener streamListener = mock(AIStreamListener.class);
        AISettings mockSettings = AISettings.builder()
            .provider(AIProvider.OLLAMA)
            .build();

        AIAnalysisExecution execution = AIAnalysisExecution.builder()
            .result(mockAnalysisResult)
            .providerUsed(AIProvider.OLLAMA)
            .build();

        AnalysisResultResponse expectedResponse = AnalysisResultResponse.builder()
            .errorSummary(mockAnalysisResult.getErrorSummary())
            .rootCause(mockAnalysisResult.getRootCause())
            .severity(mockAnalysisResult.getSeverity())
            .build();

        when(repository.fetchLogsForAnalysis("trace-123", projectId.toString()))
            .thenReturn(mockLogs);
        when(aiSettingsService.getActiveSettingsEntity())
            .thenReturn(mockSettings);
        when(aiAnalysisService.analyze(any(), any(), any()))
            .thenReturn(execution);
        when(logMapper.toResponse(mockAnalysisResult))
            .thenReturn(expectedResponse);
        when(logMapper.toHistory(any(), anyString(), anyInt(), anyLong(), anyDouble()))
            .thenReturn(AnalysisHistory.builder().fingerprint("fp-123").build());
        when(issueGroupRepository.findByProjectIdAndFingerprint(projectId, "fp-123"))
            .thenReturn(Optional.empty());
        when(remediationPullRequestService.shouldCreate(false)).thenReturn(false);

        CompletableFuture<AnalysisResultResponse> result = logAnalysisService.analyze(analyzeRequest, projectId, streamListener);
        result.get();

        verify(issueGroupRepository).save(argThat(issue ->
            issue.getProjectId().equals(projectId) &&
            issue.getFingerprint().equals("fp-123") &&
            issue.getService().equals("auth-service") &&
            issue.getErrorSummary().equals(mockAnalysisResult.getErrorSummary())
        ));
    }

    @Test
    void analyzeShouldIncrementOccurrenceCountForExistingIssue() throws Exception {
        AIStreamListener streamListener = mock(AIStreamListener.class);
        AISettings mockSettings = AISettings.builder()
            .provider(AIProvider.OLLAMA)
            .build();

        IssueGroup existingIssue = IssueGroup.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .fingerprint("fp-123")
            .service("auth-service")
            .occurrenceCount(5L)
            .firstSeen(Instant.now().minusSeconds(3600))
            .lastSeen(Instant.now())
            .build();

        AIAnalysisExecution execution = AIAnalysisExecution.builder()
            .result(mockAnalysisResult)
            .providerUsed(AIProvider.OLLAMA)
            .build();

        AnalysisResultResponse expectedResponse = AnalysisResultResponse.builder()
            .errorSummary(mockAnalysisResult.getErrorSummary())
            .rootCause(mockAnalysisResult.getRootCause())
            .severity(mockAnalysisResult.getSeverity())
            .build();

        when(repository.fetchLogsForAnalysis("trace-123", projectId.toString()))
            .thenReturn(mockLogs);
        when(aiSettingsService.getActiveSettingsEntity())
            .thenReturn(mockSettings);
        when(aiAnalysisService.analyze(any(), any(), any()))
            .thenReturn(execution);
        when(logMapper.toResponse(mockAnalysisResult))
            .thenReturn(expectedResponse);
        when(logMapper.toHistory(any(), anyString(), anyInt(), anyLong(), anyDouble()))
            .thenReturn(AnalysisHistory.builder().fingerprint("fp-123").build());
        when(issueGroupRepository.findByProjectIdAndFingerprint(projectId, "fp-123"))
            .thenReturn(Optional.of(existingIssue));
        when(remediationPullRequestService.shouldCreate(false)).thenReturn(false);

        CompletableFuture<AnalysisResultResponse> result = logAnalysisService.analyze(analyzeRequest, projectId, streamListener);
        result.get();

        verify(issueGroupRepository).save(argThat(issue ->
            issue.getOccurrenceCount() == 6L  // 5 + 1 new log
        ));
    }
}

