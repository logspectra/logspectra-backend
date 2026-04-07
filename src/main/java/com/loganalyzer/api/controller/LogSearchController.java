package com.loganalyzer.api.controller;

import com.loganalyzer.api.dto.request.AggregationRequest;
import com.loganalyzer.api.dto.request.LogSearchRequest;
import com.loganalyzer.api.dto.request.ResolveLogsRequest;
import com.loganalyzer.api.dto.response.AggregationResponse;
import com.loganalyzer.api.dto.response.LogSearchResponse;
import com.loganalyzer.api.dto.response.PagedResponse;
import com.loganalyzer.api.dto.response.ResolveLogsResponse;
import com.loganalyzer.service.LogSearchService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import com.loganalyzer.service.ProjectAccessService;
import com.loganalyzer.service.LogResolutionService;

@RestController
@RequestMapping("/api/v1/logs")
@Validated
@RequiredArgsConstructor
public class LogSearchController {

    private final LogSearchService logSearchService;
    private final ProjectAccessService projectAccessService;
    private final LogResolutionService logResolutionService;

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<LogSearchResponse>> search(@Valid LogSearchRequest request,
                                                                   Principal principal,
                                                                   @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
                                                                   @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
                                                                   @RequestParam(value = "projectId", required = false) String projectIdParam) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(logSearchService.search(request, projectId.toString()));
    }

    @GetMapping("/aggregations")
    public ResponseEntity<AggregationResponse> aggregations(@Valid AggregationRequest request,
                                                            Principal principal,
                                                            @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
                                                            @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
                                                            @RequestParam(value = "projectId", required = false) String projectIdParam) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(logSearchService.aggregate(request, projectId.toString()));
    }

    @PostMapping("/resolve")
    public ResponseEntity<ResolveLogsResponse> resolveLogs(@Valid @RequestBody ResolveLogsRequest request,
                                            Principal principal,
                                            @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
                                            @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
                                            @RequestParam(value = "projectId", required = false) String projectIdParam) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        ResolveLogsResponse response = logResolutionService.resolveLogs(request);
        return ResponseEntity.ok(response);
    }
}
