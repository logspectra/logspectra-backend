package com.loganalyzer.api.controller;

import com.loganalyzer.api.dto.request.IssueQueryRequest;
import com.loganalyzer.api.dto.response.IssueGroupResponse;
import com.loganalyzer.service.IssueGroupingService;
import com.loganalyzer.service.ProjectAccessService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class IssueController {

    private final IssueGroupingService issueGroupingService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/issues")
    public ResponseEntity<List<IssueGroupResponse>> issues(@Valid IssueQueryRequest request,
                                                           Principal principal,
                                                           @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader,
                                                           @RequestHeader(value = ProjectAccessService.PROJECT_ID_HEADER, required = false) String projectIdHeader,
                                                           @RequestParam(value = "projectId", required = false) String projectIdParam) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        UUID projectId = projectAccessService.resolveProjectId(userId, projectIdHeader, projectIdParam);
        projectAccessService.validateAccess(userId, projectId);
        return ResponseEntity.ok(issueGroupingService.getGroupedIssues(request, projectId));
    }
}
