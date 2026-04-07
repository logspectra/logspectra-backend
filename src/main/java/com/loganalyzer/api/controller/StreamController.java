package com.loganalyzer.api.controller;

import com.loganalyzer.service.LogStreamingService;
import com.loganalyzer.service.ProjectAccessService;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = {"X-User-ID", "X-Project-ID"})
public class StreamController {

    private final LogStreamingService logStreamingService;
    private final ProjectAccessService projectAccessService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String service,
                             @RequestParam(required = false) String level,
                             @RequestParam(value = "projectId", required = false) String projectIdParam,
                             Principal principal,
                             @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
                             @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        // Create SSE emitter with streaming service
        return logStreamingService.createEmitter(projectId.toString(), service, level);
    }
}
