package com.loganalyzer.service;

import com.loganalyzer.api.dto.request.CreateProjectRequest;
import com.loganalyzer.api.dto.response.ProjectResponse;
import com.loganalyzer.domain.model.Project;
import com.loganalyzer.domain.model.UserProject;
import com.loganalyzer.domain.model.UserProjectId;
import com.loganalyzer.domain.model.UserProjectRole;
import com.loganalyzer.domain.repository.ProjectRepository;
import com.loganalyzer.domain.repository.UserProjectRepository;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserProjectRepository userProjectRepository;
    private final UserService userService;
    private final LogElasticsearchRepository logElasticsearchRepository;

    @Transactional
    public ProjectResponse createProject(UUID userId, CreateProjectRequest request) {
        Project project = projectRepository.save(Project.builder()
            .name(request.getName().trim())
            .description(request.getDescription())
            .build());
        userProjectRepository.save(UserProject.builder()
            .id(new UserProjectId(userId, project.getId()))
            .role(UserProjectRole.ADMIN)
            .build());
        return toResponse(project, UserProjectRole.ADMIN, serviceCountForProject(project.getId()));
    }

    public List<ProjectResponse> listProjects(UUID userId) {
        if (userService.isAdmin(userId)) {
            return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(Project::getCreatedAt).reversed())
                .map(project -> toResponse(project, UserProjectRole.ADMIN, serviceCountForProject(project.getId())))
                .toList();
        }

        Map<UUID, UserProjectRole> memberships = userProjectRepository.findAllByIdUserId(userId).stream()
            .collect(java.util.stream.Collectors.toMap(up -> up.getId().getProjectId(), UserProject::getRole));
        return projectRepository.findAllById(memberships.keySet())
            .stream()
            .sorted(Comparator.comparing(Project::getCreatedAt).reversed())
            .map(project -> toResponse(project, memberships.get(project.getId()), serviceCountForProject(project.getId())))
            .toList();
    }

    public ProjectResponse getProject(UUID userId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (userService.isAdmin(userId)) {
            return toResponse(project, UserProjectRole.ADMIN, serviceCountForProject(project.getId()));
        }

        UserProject membership = userProjectRepository.findById(new UserProjectId(userId, projectId))
            .orElseThrow(() -> new ResourceNotFoundException("Project not found for this user"));
        return toResponse(project, membership.getRole(), serviceCountForProject(project.getId()));
    }

    @Transactional
    public void deleteProject(UUID userId, UUID projectId) {
        if (!userService.isAdmin(userId)) {
            UserProject membership = userProjectRepository.findById(new UserProjectId(userId, projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found for this user"));
            if (membership.getRole() != UserProjectRole.ADMIN) {
                throw new IllegalArgumentException("Only project admins can delete project");
            }
        }
        userProjectRepository.deleteAllByIdProjectId(projectId);
        projectRepository.deleteById(projectId);
    }

    private ProjectResponse toResponse(Project project, UserProjectRole role, int serviceCount) {
        return ProjectResponse.builder()
            .id(project.getId())
            .name(project.getName())
            .description(project.getDescription())
            .createdAt(project.getCreatedAt())
            .updatedAt(project.getUpdatedAt())
            .role(role)
            .serviceCount(serviceCount)
            .build();
    }

    private int serviceCountForProject(UUID projectId) {
        try {
            return logElasticsearchRepository.totalServices(projectId.toString()).size();
        } catch (IOException ex) {
            return 0;
        }
    }
}
