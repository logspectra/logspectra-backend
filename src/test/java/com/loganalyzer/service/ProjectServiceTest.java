package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import com.loganalyzer.api.dto.response.ProjectResponse;
import com.loganalyzer.domain.model.Project;
import com.loganalyzer.domain.model.UserProject;
import com.loganalyzer.domain.model.UserProjectId;
import com.loganalyzer.domain.model.UserProjectRole;
import com.loganalyzer.domain.repository.ProjectRepository;
import com.loganalyzer.domain.repository.UserProjectRepository;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserProjectRepository userProjectRepository;

    @Mock
    private UserService userService;

    @Mock
    private LogElasticsearchRepository logElasticsearchRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void listProjectsShouldIncludeServiceCount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
            .id(projectId)
            .name("proj")
            .description("desc")
            .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-04-01T00:00:00Z"))
            .build();

        when(userService.isAdmin(userId)).thenReturn(false);
        when(userProjectRepository.findAllByIdUserId(userId)).thenReturn(List.of(
            UserProject.builder().id(new UserProjectId(userId, projectId)).role(UserProjectRole.ADMIN).build()
        ));
        when(projectRepository.findAllById(anyIterable())).thenReturn(List.of(project));
        when(logElasticsearchRepository.totalServices(projectId.toString())).thenReturn(List.of("api", "auth", "billing"));

        List<ProjectResponse> response = projectService.listProjects(userId);

        assertEquals(1, response.size());
        assertEquals(3, response.getFirst().getServiceCount());
    }

    @Test
    void getProjectShouldFallbackServiceCountToZeroWhenLogStoreFails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
            .id(projectId)
            .name("proj")
            .description("desc")
            .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-04-01T00:00:00Z"))
            .build();

        when(userService.isAdmin(userId)).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(java.util.Optional.of(project));
        when(logElasticsearchRepository.totalServices(projectId.toString())).thenThrow(new IOException("ES unavailable"));

        ProjectResponse response = projectService.getProject(userId, projectId);

        assertEquals(0, response.getServiceCount());
        assertEquals(UserProjectRole.ADMIN, response.getRole());
    }
}

