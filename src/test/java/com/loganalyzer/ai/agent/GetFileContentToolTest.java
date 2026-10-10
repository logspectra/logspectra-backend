package com.loganalyzer.ai.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.github.GitHubGitClient;
import com.loganalyzer.github.GitHubRepoRef;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetFileContentToolTest {

    @Mock
    private GitHubGitClient gitHubGitClient;

    private GetFileContentTool tool;
    private AgentSession session;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new GetFileContentTool(gitHubGitClient);
        session = new AgentSession(
            new GitHubRepoRef("acme", "gateway-service"),
            "main",
            ServiceSourceMapping.builder().serviceName("gateway-service").gitUrl("https://github.com/acme/gateway-service.git").build(),
            "proj",
            "gateway-service",
            100,
            10_000
        );
    }

    @Test
    void returnsFileBody() {
        when(gitHubGitClient.getFileContent(any(), eq("src/main/java/Foo.java"), eq("main")))
            .thenReturn(Optional.of("class Foo {}"));
        String result = tool.execute(session, mapper.createObjectNode().put("path", "src/main/java/Foo.java"));
        assertTrue(result.contains("class Foo {}"));
        assertTrue(session.getFetchedPaths().contains("src/main/java/Foo.java"));
    }
}
