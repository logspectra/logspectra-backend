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

        when(gitHubGitClient.getBranchTipSha(any(), anyString())).thenReturn(Optional.empty());
        when(gitHubGitClient.getDefaultBranchSha(any(), eq("main"))).thenReturn("base-sha");
        when(gitHubGitClient.getCommitTreeSha(any(), eq("base-sha"))).thenReturn("tree-sha");
        when(gitHubGitClient.findOpenPullRequest(any(), anyString())).thenReturn(Optional.empty());
        when(gitHubGitClient.createBlob(any(), anyString())).thenReturn("blob-sha");
        when(gitHubGitClient.createTree(any(), eq("tree-sha"), anyList())).thenReturn("new-tree");
        when(gitHubGitClient.createCommit(any(), anyString(), eq("new-tree"), eq("base-sha"), anyString(), anyString()))
            .thenReturn("commit-sha");
        when(gitHubGitClient.createPullRequest(any(), anyString(), anyString(), anyString(), eq("main")))
            .thenReturn(Map.of("number", 42, "html_url", "https://github.com/acme/gateway-service/pull/42"));

        AnalysisResult analysis = AnalysisResult.builder()
            .fingerprint("abcdef123456")
            .errorSummary("Null pointer in checkout")
            .rootCause("Missing null check")
            .severity("HIGH")
            .suggestedFixes(List.of(SuggestedFix.builder()
                .fix("Add null guard")
                .priority("HIGH")
                .effort("LOW")
                .filePath("src/main/java/com/example/Checkout.java")
                .newContent("class Checkout {}")
                .build()))
            .build();

        Optional<RemediationPullRequestResult> result =
            service.createForAnalysis(projectId, "gateway-service", analysis);

        assertTrue(result.isPresent());
        assertEquals("https://github.com/acme/gateway-service/pull/42", result.get().getPullRequestUrl());
        assertEquals(42, result.get().getPullRequestNumber());
        assertEquals("logspectra/fix-gateway-service-abcdef12", result.get().getBranch());
        verify(gitHubGitClient).createBranchRef(any(), eq("logspectra/fix-gateway-service-abcdef12"), eq("commit-sha"));
        verify(gitHubGitClient, never()).updateBranchRef(any(), anyString(), anyString());
        verify(gitHubGitClient, times(2)).createBlob(any(), anyString());
        verify(gitHubGitClient).createTree(any(), eq("tree-sha"), anyList());
    }

    @Test
    void createForAnalysisReusesBranchAndPrOnSecondDebug() {
        UUID projectId = UUID.randomUUID();
        String branch = "logspectra/fix-gateway-service-abcdef12";
        when(codeRegistryService.find(eq(projectId.toString()), eq("gateway-service")))
            .thenReturn(Optional.of(ServiceSourceMapping.builder()
                .serviceName("gateway-service")
                .gitUrl("https://github.com/acme/gateway-service.git")
                .defaultBranch("main")
                .build()));

        when(gitHubGitClient.getBranchTipSha(any(), eq(branch))).thenReturn(Optional.of("tip-sha"));
        when(gitHubGitClient.getCommitTreeSha(any(), eq("tip-sha"))).thenReturn("tip-tree");
        when(gitHubGitClient.createBlob(any(), anyString())).thenReturn("blob-sha");
        when(gitHubGitClient.createTree(any(), eq("tip-tree"), anyList())).thenReturn("new-tree");
        when(gitHubGitClient.createCommit(any(), anyString(), eq("new-tree"), eq("tip-sha"), anyString(), anyString()))
            .thenReturn("commit-sha-2");
        when(gitHubGitClient.findOpenPullRequest(any(), eq(branch)))
            .thenReturn(Optional.of(Map.of("number", 7, "html_url", "https://github.com/acme/gateway-service/pull/7")));

        AnalysisResult analysis = AnalysisResult.builder()
            .fingerprint("abcdef123456")
            .errorSummary("Still failing")
            .rootCause("Same bug")
            .build();

        Optional<RemediationPullRequestResult> result =
            service.createForAnalysis(projectId, "gateway-service", analysis);

        assertTrue(result.isPresent());
        assertEquals(branch, result.get().getBranch());
        assertEquals(7, result.get().getPullRequestNumber());
        verify(gitHubGitClient).updateBranchRef(any(), eq(branch), eq("commit-sha-2"));
        verify(gitHubGitClient, never()).createBranchRef(any(), anyString(), anyString());
        verify(gitHubGitClient, never()).createPullRequest(any(), anyString(), anyString(), anyString(), anyString());
        verify(gitHubGitClient, never()).getDefaultBranchSha(any(), anyString());
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
