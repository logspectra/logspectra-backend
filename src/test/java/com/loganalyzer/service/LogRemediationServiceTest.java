package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loganalyzer.api.dto.request.RemediateRequest;
import com.loganalyzer.api.dto.response.RemediationResponse;
import com.loganalyzer.domain.model.IssueGroup;
import com.loganalyzer.domain.model.SuggestedFix;
import com.loganalyzer.domain.repository.IssueGroupRepository;
import com.loganalyzer.github.RemediationPullRequestResult;
import com.loganalyzer.github.RemediationPullRequestService;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogRemediationServiceTest {

    @Mock
    private IssueGroupRepository issueGroupRepository;

    @Mock
    private RemediationPullRequestService remediationPullRequestService;

    @InjectMocks
    private LogRemediationService logRemediationService;

    @Test
    void applySuggestedFixesOpensPr() {
        UUID projectId = UUID.randomUUID();
        IssueGroup issue = IssueGroup.builder()
            .fingerprint("fp-abc")
            .projectId(projectId)
            .service("gateway-service")
            .errorSummary("err")
            .rootCause("cause")
            .severity("HIGH")
            .build();
        issue.setSuggestedFixes(List.of(SuggestedFix.builder()
            .filePath("src/Foo.java")
            .search("a")
            .newContent("full-file")
            .build()));

        when(remediationPullRequestService.isRemediationEnabled()).thenReturn(true);
        when(issueGroupRepository.findByProjectIdAndFingerprint(projectId, "fp-abc"))
            .thenReturn(Optional.of(issue));
        when(remediationPullRequestService.createForAnalysis(eq(projectId), eq("gateway-service"), any()))
            .thenReturn(Optional.of(RemediationPullRequestResult.builder()
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .branch("logspectra/fix-gateway-fp-abc")
                .pullRequestNumber(1)
                .build()));

        RemediationResponse response = logRemediationService.applySuggestedFixes(
            RemediateRequest.builder().fingerprint("fp-abc").traceId("t-1").build(),
            projectId);

        assertEquals("https://github.com/org/repo/pull/1", response.getRemediationPrUrl());
        assertEquals(1, response.getPatchesApplied(), "counts file paths with search or newContent");
        verify(remediationPullRequestService).createForAnalysis(eq(projectId), eq("gateway-service"), any());
    }

    @Test
    void applySuggestedFixesRequiresPriorDebug() {
        UUID projectId = UUID.randomUUID();
        when(remediationPullRequestService.isRemediationEnabled()).thenReturn(true);
        when(issueGroupRepository.findByProjectIdAndFingerprint(projectId, "missing"))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            logRemediationService.applySuggestedFixes(
                RemediateRequest.builder().fingerprint("missing").build(),
                projectId));
    }
}
