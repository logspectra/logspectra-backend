package com.loganalyzer.api.controller;

import com.loganalyzer.api.dto.request.AnalyzeRequest;
import com.loganalyzer.infrastructure.ratelimit.RateLimited;
import com.loganalyzer.service.LogAnalysisStreamingService;
import com.loganalyzer.service.ProjectAccessService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogAnalysisController {

    private final LogAnalysisStreamingService logAnalysisStreamingService;
    private final ProjectAccessService projectAccessService;

    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimited(key = "analyze", limit = 20, windowSeconds = 60)
    public SseEmitter analyze(@Valid @RequestBody AnalyzeRequest request,
                              Principal principal,
                              @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
                              @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
                              @RequestParam(value = "projectId", required = false) String projectIdParam) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);

        SseEmitter emitter = new SseEmitter(120_000L);
        logAnalysisStreamingService.streamAnalysis(request, projectId, emitter);
        return emitter;
    }
}
