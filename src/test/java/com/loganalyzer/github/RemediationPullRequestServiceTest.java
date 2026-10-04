package com.loganalyzer.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.SuggestedFix;
import com.loganalyzer.service.ServiceCodeRegistryService;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemediationPullRequestServiceTest {

    @Mock
    private GitHubGitClient gitHubGitClient;
    @Mock
    private ServiceCodeRegistryService codeRegistryService;

    private GitHubAppProperties properties;
    private RemediationPullRequestService service;

    @BeforeEach
    void setUp() {
        properties = new GitHubAppProperties();
        properties.getApp().setEnabled(true);
        properties.getPr().setEnabled(true);
        properties.getPr().setBranchPrefix("logspectra/fix");
        properties.getPr().setCommitAuthorName("LogSpectra");
        properties.getPr().setCommitAuthorEmail("logspectra@users.noreply.github.com");
        service = new RemediationPullRequestService(properties, gitHubGitClient, codeRegistryService);
    }

    @Test
    void shouldCreateWhenRequestFlag() {
        assertTrue(service.shouldCreate(true));
        assertFalse(service.shouldCreate(false));
        properties.getPr().setAutoCreateAfterAnalysis(true);
        assertTrue(service.shouldCreate(false));
    }

    @Test
    void createForAnalysisOpensPr() {
        UUID projectId = UUID.randomUUID();
        when(codeRegistryService.find(eq(projectId.toString()), eq("gateway-service")))
            .thenReturn(Optional.of(ServiceSourceMapping.builder()
                .serviceName("gateway-service")
                .gitUrl("https://github.com/acme/gateway-service.git")
                .defaultBranch("main")
                .build()));

        when(gitHubGitClient.getDefaultBranchSha(any(), eq("main"))).thenReturn("base-sha");
        when(gitHubGitClient.getCommitTreeSha(any(), eq("base-sha"))).thenReturn("tree-sha");
        when(gitHubGitClient.createBlob(any(), anyString())).thenReturn("blob-sha");
        when(gitHubGitClient.createTree(any(), eq("tree-sha"), anyString(), eq("blob-sha"))).thenReturn("new-tree");
        when(gitHubGitClient.createCommit(any(), anyString(), eq("new-tree"), eq("base-sha"), anyString(), anyString()))
            .thenReturn("commit-sha");
        when(gitHubGitClient.createPullRequest(any(), anyString(), anyString(), anyString(), eq("main")))
            .thenReturn(Map.of("number", 42, "html_url", "https://github.com/acme/gateway-service/pull/42"));

        AnalysisResult analysis = AnalysisResult.builder()
            .fingerprint("abcdef123456")
            .errorSummary("Null pointer in checkout")
            .rootCause("Missing null check")
            .severity("HIGH")
            .suggestedFixes(List.of(SuggestedFix.builder().fix("Add null guard").priority("HIGH").effort("LOW").build()))
            .build();

        Optional<RemediationPullRequestResult> result =
            service.createForAnalysis(projectId, "gateway-service", analysis);

        assertTrue(result.isPresent());
        assertEquals("https://github.com/acme/gateway-service/pull/42", result.get().getPullRequestUrl());
        assertEquals(42, result.get().getPullRequestNumber());
        assertTrue(result.get().getBranch().startsWith("logspectra/fix-gateway-service-abcdef12-"));
        verify(gitHubGitClient).createBranchRef(any(), eq(result.get().getBranch()), eq("commit-sha"));
    }

    @Test
    void skipsWhenNoRegistry() {
        UUID projectId = UUID.randomUUID();
        when(codeRegistryService.find(anyString(), anyString())).thenReturn(Optional.empty());
        Optional<RemediationPullRequestResult> result = service.createForAnalysis(
            projectId, "gateway-service", AnalysisResult.builder().fingerprint("x").build());
        assertTrue(result.isEmpty());
        verifyNoInteractions(gitHubGitClient);
    }
}
