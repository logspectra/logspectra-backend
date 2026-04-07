package com.loganalyzer.service;

import com.loganalyzer.domain.model.Project;
import com.loganalyzer.domain.model.UserProject;
import com.loganalyzer.domain.repository.ProjectRepository;
import com.loganalyzer.domain.repository.UserProjectRepository;
import com.loganalyzer.infrastructure.exception.ProjectAccessDeniedException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    public static final String PROJECT_ID_HEADER = "X-Project-Id";
    public static final String USER_ID_HEADER = "X-User-Id";

    private final UserProjectRepository userProjectRepository;
    private final ProjectRepository projectRepository;
    private final UserService userService;

    public UUID resolveUserId(Principal principal, String userIdHeader) {
        if (StringUtils.hasText(userIdHeader)) {
            try {
                return UUID.fromString(userIdHeader.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid user id format; expected UUID");
            }
        }

        if (principal != null && StringUtils.hasText(principal.getName())) {
            return userService.findUserIdByUsername(principal.getName());
        }

        throw new IllegalArgumentException("Missing user identity. Provide X-User-Id header or authenticate with a principal");
    }

    public UUID resolveProjectId(UUID userId, String explicitProjectId, String queryProjectId) {
        String candidate = StringUtils.hasText(explicitProjectId) ? explicitProjectId : queryProjectId;
        if (!StringUtils.hasText(candidate)) {
            List<UserProject> memberships = userProjectRepository.findAllByIdUserId(userId);
            if (memberships.isEmpty()) {
                throw new IllegalArgumentException("No projects assigned to the user");
            }
            if (memberships.size() == 1) {
                return memberships.get(0).getId().getProjectId();
            }
            throw new IllegalArgumentException("Missing project context. Provide X-Project-Id header or projectId query parameter");
        }
        try {
            return UUID.fromString(candidate.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid project id format; expected UUID");
        }
    }

    public void validateAccess(UUID userId, UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new IllegalArgumentException("Project does not exist: " + projectId);
        }
        if (userService.isAdmin(userId)) {
            return;
        }
        if (!userProjectRepository.existsByIdUserIdAndIdProjectId(userId, projectId)) {
            throw new ProjectAccessDeniedException("User is not authorized for this project");
        }
    }

    public List<Project> getAccessibleProjects(UUID userId) {
        if (userService.isAdmin(userId)) {
            return projectRepository.findAll();
        }
        List<UUID> projectIds = userProjectRepository.findAllByIdUserId(userId)
            .stream()
            .map(up -> up.getId().getProjectId())
            .toList();
        return projectIds.isEmpty() ? List.of() : projectRepository.findAllById(projectIds);
    }
}
