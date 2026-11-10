package com.loganalyzer.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitWorkspaceServiceTest {

    private GitWorkspaceService service;

    @BeforeEach
    void setUp() {
        GitHubAppProperties properties = new GitHubAppProperties();
        properties.getApp().setEnabled(true);
        properties.getWorkspace().setEnabled(true);
        properties.getWorkspace().setRoot("/tmp/logspectra-code-registry");
        service = new GitWorkspaceService(properties, mock(GitHubAppTokenService.class), mock(GitCliRunner.class));
    }

    @Test
    void workspacePathUsesProjectAndServiceSegments() {
        Path path = service.workspacePath("3f347bb4-e61e-4957-a45a-929b1ae7bcfe", "gateway-service");
        assertTrue(path.toString().contains("3f347bb4-e61e-4957-a45a-929b1ae7bcfe"));
        assertTrue(path.toString().endsWith("gateway-service"));
        assertEquals(Path.of("/tmp/logspectra-code-registry")
            .resolve("3f347bb4-e61e-4957-a45a-929b1ae7bcfe")
            .resolve("gateway-service")
            .normalize(), path.normalize());
    }

    @Test
    void isActiveRequiresAppAndWorkspace() {
        GitHubAppProperties properties = new GitHubAppProperties();
        properties.getApp().setEnabled(false);
        properties.getWorkspace().setEnabled(true);
        GitWorkspaceService inactive = new GitWorkspaceService(properties, mock(GitHubAppTokenService.class), mock(GitCliRunner.class));
        assertTrue(!inactive.isActive());
    }
}
