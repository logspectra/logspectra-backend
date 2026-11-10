package com.loganalyzer.api.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.loganalyzer.api.dto.request.AnalyzeRequest;
import com.loganalyzer.service.LogAnalysisStreamingService;
import com.loganalyzer.service.ProjectAccessService;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class LogControllerTest {

    @Mock
    private LogAnalysisStreamingService logAnalysisStreamingService;

    @Mock
    private ProjectAccessService projectAccessService;

    @InjectMocks
    private LogAnalysisController logAnalysisController;

    private UUID userId;
    private UUID projectId;
    private AnalyzeRequest analyzeRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        analyzeRequest = new AnalyzeRequest();
        analyzeRequest.setTraceId("trace-123");
    }

    @Test
    void analyzeShouldResolveAccessAndStartStreaming() {
        Principal principal = () -> "user@example.com";

        when(projectAccessService.resolveUserId(principal, null)).thenReturn(userId);
        when(projectAccessService.resolveProjectId(userId, null, projectId.toString())).thenReturn(projectId);
        when(logAnalysisStreamingService.streamAnalysis(eq(analyzeRequest), eq(projectId)))
            .thenReturn(new SseEmitter(60_000L));

        SseEmitter emitter = logAnalysisController.analyze(analyzeRequest, principal, null, null, projectId.toString());

        assertNotNull(emitter);
        verify(projectAccessService).resolveUserId(principal, null);
        verify(projectAccessService).resolveProjectId(userId, null, projectId.toString());
        verify(projectAccessService).validateAccess(userId, projectId);
        verify(logAnalysisStreamingService).streamAnalysis(eq(analyzeRequest), eq(projectId));
    }
}

