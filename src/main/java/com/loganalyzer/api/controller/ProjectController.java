package com.loganalyzer.api.controller;

import com.loganalyzer.api.dto.request.CreateProjectRequest;
import com.loganalyzer.api.dto.response.ProjectResponse;
import com.loganalyzer.service.ProjectAccessService;
import com.loganalyzer.service.ProjectService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request,
                                                         Principal principal,
                                                         @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        return ResponseEntity.ok(projectService.createProject(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects(Principal principal,
                                                              @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        return ResponseEntity.ok(projectService.listProjects(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id,
                                                      Principal principal,
                                                      @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        return ResponseEntity.ok(projectService.getProject(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id,
                                              Principal principal,
                                              @RequestHeader(value = ProjectAccessService.USER_ID_HEADER, required = false) String userIdHeader) {
        UUID userId = projectAccessService.resolveUserId(principal, userIdHeader);
        projectService.deleteProject(userId, id);
        return ResponseEntity.noContent().build();
    }
}
