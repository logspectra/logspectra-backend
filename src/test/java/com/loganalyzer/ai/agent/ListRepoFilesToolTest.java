package com.loganalyzer.ai.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loganalyzer.github.GitHubGitClient;
import com.loganalyzer.github.GitHubRepoRef;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListRepoFilesToolTest {

    @Mock
    private GitHubGitClient gitHubGitClient;

    private ListRepoFilesTool tool;
    private AgentSession session;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new ListRepoFilesTool(gitHubGitClient);
        session = new AgentSession(
            new GitHubRepoRef("acme", "gateway-service"),
            "main",
            ServiceSourceMapping.builder().serviceName("gateway-service").gitUrl("https://github.com/acme/gateway-service.git").build(),
            "proj",
            "gateway-service",
            100,
            10_000
        );
        when(gitHubGitClient.listFilePaths(any(), eq("main"), eq(100))).thenReturn(List.of(
            "src/main/java/com/example/DemoController.java",
            "src/main/java/com/example/Other.java",
            "README.md"
        ));
    }

    @Test
    void filtersByQuery() {
        ObjectNode args = mapper.createObjectNode().put("query", "DemoController");
        String result = tool.execute(session, args);
        assertTrue(result.contains("DemoController.java"));
        assertTrue(!result.contains("Other.java"));
    }
}
