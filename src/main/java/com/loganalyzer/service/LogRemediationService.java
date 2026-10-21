package com.loganalyzer.service;

import com.loganalyzer.api.dto.request.RemediateRequest;
import com.loganalyzer.api.dto.response.RemediationResponse;
import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.IssueGroup;
import com.loganalyzer.domain.model.SuggestedFix;
import com.loganalyzer.domain.repository.IssueGroupRepository;
import com.loganalyzer.github.RemediationPullRequestResult;
import com.loganalyzer.github.RemediationPullRequestService;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogRemediationService {

    private final IssueGroupRepository issueGroupRepository;
    private final RemediationPullRequestService remediationPullRequestService;

    public RemediationResponse applySuggestedFixes(RemediateRequest request, UUID projectId) {
        if (!remediationPullRequestService.isRemediationEnabled()) {
            throw new IllegalStateException("GitHub remediation is disabled — enable the GitHub App and PR settings");
        }

        String fingerprint = request.getFingerprint().trim();
        IssueGroup issue = issueGroupRepository.findByProjectIdAndFingerprint(projectId, fingerprint)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No debug analysis found for this issue — run Debug first"));

        List<SuggestedFix> fixes = issue.getSuggestedFixes();
        if (fixes == null || fixes.isEmpty()) {
            throw new IllegalArgumentException("This analysis has no suggested fixes to apply");
        }

        AnalysisResult analysis = toAnalysisResult(issue);
        log.info("operation=applyRemediation feature=remediate-fix status=entry projectId={} service={} fingerprint={} traceId={}",
            projectId, issue.getService(), fingerprint, request.getTraceId());

        Optional<RemediationPullRequestResult> pr = remediationPullRequestService.createForAnalysis(
            projectId, issue.getService(), analysis);

        if (pr.isEmpty()) {
            throw new IllegalStateException(
                "Remediation PR could not be created — verify the service git URL in the code registry and GitHub App access");
        }

        RemediationPullRequestResult created = pr.get();
        int patchCount = countApplicableFixes(fixes);
        return RemediationResponse.builder()
            .fingerprint(fingerprint)
            .service(issue.getService())
            .patchesApplied(patchCount)
            .remediationPrUrl(created.getPullRequestUrl())
            .remediationBranch(created.getBranch())
            .remediationPrNumber(created.getPullRequestNumber())
            .message("Remediation branch updated and pull request " + (created.getPullRequestNumber() != null
                ? "#" + created.getPullRequestNumber()
                : "opened"))
            .build();
    }

    private static AnalysisResult toAnalysisResult(IssueGroup issue) {
        return AnalysisResult.builder()
            .fingerprint(issue.getFingerprint())
            .errorSummary(issue.getErrorSummary())
            .rootCause(issue.getRootCause())
            .severity(issue.getSeverity())
            .affectedServices(List.of(issue.getService()))
            .suggestedFixes(issue.getSuggestedFixes())
            .analyzedAt(Instant.now())
            .build();
    }

    private static int countApplicableFixes(List<SuggestedFix> fixes) {
        int count = 0;
        for (SuggestedFix fix : fixes) {
            if (!StringUtils.hasText(fix.getFilePath())) {
                continue;
            }
            if (StringUtils.hasText(fix.getSearch()) || StringUtils.hasText(fix.getNewContent())) {
                count++;
            }
        }
        return count;
    }
}
