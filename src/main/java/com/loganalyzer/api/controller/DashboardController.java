package com.loganalyzer.api.controller;

import com.loganalyzer.api.dto.response.DashboardStatsResponse;
import com.loganalyzer.service.DashboardStatsService;
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
@RequestMapping("/api/v1/dashboard")
@Validated
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardStatsService dashboardStatsService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> stats(
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
        return ResponseEntity.ok(dashboardStatsService.getStats(projectId, from, to));
    }
}

