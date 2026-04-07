package com.loganalyzer.api.controller;

import com.loganalyzer.api.dto.response.AiUsageResponse;
import com.loganalyzer.api.dto.response.ErrorResolutionResponse;
import com.loganalyzer.api.dto.response.IngestionRateResponse;
import com.loganalyzer.api.dto.response.SeverityMixResponse;
import com.loganalyzer.api.dto.response.TopEndpointResponse;
import com.loganalyzer.api.dto.response.WeeklyTrendResponse;
import com.loganalyzer.service.AnalyticsTrendService;
import com.loganalyzer.service.ProjectAccessService;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@Validated
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsTrendService analyticsTrendService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/weekly-trend")
    public ResponseEntity<WeeklyTrendResponse> weeklyTrend(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Principal principal,
        @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
        @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
        @RequestParam(value = "projectId", required = false) String projectIdParam
    ) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(analyticsTrendService.getWeeklyTrend(projectId, from, to));
    }

    @GetMapping("/ai-usage")
    public ResponseEntity<AiUsageResponse> aiUsage(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Principal principal,
        @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
        @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
        @RequestParam(value = "projectId", required = false) String projectIdParam
    ) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(analyticsTrendService.getAiUsage(projectId, from, to));
    }

    @GetMapping("/ingestion-rate")
    public ResponseEntity<IngestionRateResponse> ingestionRate(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Principal principal,
        @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
        @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
        @RequestParam(value = "projectId", required = false) String projectIdParam
    ) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(analyticsTrendService.getIngestionRate(projectId, from, to));
    }

    @GetMapping("/severity-mix")
    public ResponseEntity<SeverityMixResponse> severityMix(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Principal principal,
        @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
        @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
        @RequestParam(value = "projectId", required = false) String projectIdParam
    ) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(analyticsTrendService.getSeverityMix(projectId, from, to));
    }

    @GetMapping("/error-resolution")
    public ResponseEntity<ErrorResolutionResponse> errorResolution(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Principal principal,
        @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
        @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
        @RequestParam(value = "projectId", required = false) String projectIdParam
    ) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(analyticsTrendService.getErrorResolution(projectId, from, to));
    }

    @GetMapping("/top-endpoints")
    public ResponseEntity<TopEndpointResponse> topEndpoints(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        Principal principal,
        @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
        @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
        @RequestParam(value = "projectId", required = false) String projectIdParam
    ) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(analyticsTrendService.getTopEndpoints(projectId, from, to));
    }
}

