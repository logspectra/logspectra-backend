package com.loganalyzer.github;

import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.SuggestedFix;
import com.loganalyzer.service.ServiceCodeRegistryService;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemediationPullRequestService {

    private static final DateTimeFormatter BRANCH_TS =
        DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final GitHubAppProperties properties;
    private final GitHubGitClient gitHubGitClient;
    private final ServiceCodeRegistryService codeRegistryService;

    public boolean shouldCreate(boolean requestFlag) {
        return properties.getApp().isEnabled()
            && properties.getPr().isEnabled()
            && (requestFlag || properties.getPr().isAutoCreateAfterAnalysis());
    }

    /**
     * Opens a remediation PR: commits AI source patches (when present) plus a markdown brief.
     * Uses GitHub REST only — no local clone.
     */
    public Optional<RemediationPullRequestResult> createForAnalysis(
        UUID projectId,
        String serviceName,
        AnalysisResult analysis
    ) {
        if (!properties.getApp().isEnabled() || !properties.getPr().isEnabled()) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(serviceName)) {
            log.warn("operation=createRemediationPr feature=github-app-pr status=error error=missingService");
            return Optional.empty();
        }

        long started = System.currentTimeMillis();
        Optional<ServiceSourceMapping> mapping = codeRegistryService.find(projectId.toString(), serviceName);
        if (mapping.isEmpty() || !StringUtils.hasText(mapping.get().getGitUrl())) {
            log.warn("operation=createRemediationPr feature=github-app-pr status=error service={} error=noGitUrlInRegistry",
                serviceName);
            return Optional.empty();
        }

        Optional<GitHubRepoRef> repoOpt = GitHubRepoRef.parse(mapping.get().getGitUrl());
        if (repoOpt.isEmpty()) {
            log.warn("operation=createRemediationPr feature=github-app-pr status=error gitUrl={} error=notGitHubUrl",
                mapping.get().getGitUrl());
            return Optional.empty();
        }

        GitHubRepoRef repo = repoOpt.get();
        String baseBranch = StringUtils.hasText(mapping.get().getDefaultBranch())
            ? mapping.get().getDefaultBranch()
            : "main";
        String fingerprint = StringUtils.hasText(analysis.getFingerprint())
            ? analysis.getFingerprint()
            : UUID.randomUUID().toString();
        String shortFp = fingerprint.length() > 8 ? fingerprint.substring(0, 8) : fingerprint;
        String branch = sanitizeBranch(properties.getPr().getBranchPrefix()
            + "-" + serviceName
            + "-" + shortFp
            + "-" + BRANCH_TS.format(Instant.now()));

        try {
            String baseSha = gitHubGitClient.getDefaultBranchSha(repo, baseBranch);
            String baseTree = gitHubGitClient.getCommitTreeSha(repo, baseSha);

            Map<String, String> filesToCommit = new LinkedHashMap<>();
            List<String> patchedPaths = collectSourcePatches(analysis, filesToCommit);
            String markdownPath = ".logspectra/remediations/" + shortFp + ".md";
            filesToCommit.put(markdownPath, buildRemediationMarkdown(projectId, serviceName, analysis, fingerprint));

            List<GitHubGitClient.TreeEntry> treeEntries = new ArrayList<>();
            for (Map.Entry<String, String> file : filesToCommit.entrySet()) {
                String blobSha = gitHubGitClient.createBlob(repo, file.getValue());
                treeEntries.add(new GitHubGitClient.TreeEntry(file.getKey(), blobSha));
            }
            String treeSha = gitHubGitClient.createTree(repo, baseTree, treeEntries);

            boolean hasSourcePatches = !patchedPaths.isEmpty();
            String message = hasSourcePatches
                ? "fix(logspectra): remediate " + shortFp + " (" + patchedPaths.size() + " file(s))"
                : "docs(logspectra): remediation brief for " + shortFp;
            String commitSha = gitHubGitClient.createCommit(
                repo,
                message,
                treeSha,
                baseSha,
                properties.getPr().getCommitAuthorName(),
                properties.getPr().getCommitAuthorEmail()
            );
            gitHubGitClient.createBranchRef(repo, branch, commitSha);

            String title = "[LogSpectra] " + truncate(
                StringUtils.hasText(analysis.getErrorSummary()) ? analysis.getErrorSummary() : "Remediation",
                72
            );
            String prBody = buildPullRequestBody(serviceName, analysis, markdownPath, fingerprint, patchedPaths);
            Map<String, Object> pr = gitHubGitClient.createPullRequest(repo, title, prBody, branch, baseBranch);
            Integer number = pr.get("number") instanceof Number n ? n.intValue() : null;
            String htmlUrl = pr.get("html_url") != null ? String.valueOf(pr.get("html_url")) : null;

            if (number != null) {
                List<String> reviewers = parseReviewers(properties.getPr().getReviewers());
                try {
                    gitHubGitClient.requestReviewers(repo, number, reviewers);
                } catch (RuntimeException ex) {
                    log.warn("operation=createRemediationPr feature=github-app-pr status=error step=requestReviewers error={}",
                        ex.getMessage());
                }
            }

            RemediationPullRequestResult result = RemediationPullRequestResult.builder()
                .owner(repo.owner())
                .repo(repo.repo())
                .branch(branch)
                .baseBranch(baseBranch)
                .pullRequestUrl(htmlUrl)
                .pullRequestNumber(number)
                .commitSha(commitSha)
                .build();

            log.info("operation=createRemediationPr feature=github-app-pr status=exit service={} repo={} branch={} pr={} sourceFiles={} durationMs={}",
                serviceName, repo.fullName(), branch, htmlUrl, patchedPaths.size(), System.currentTimeMillis() - started);
            return Optional.of(result);
        } catch (RuntimeException ex) {
            log.error("operation=createRemediationPr feature=github-app-pr status=error service={} repo={} error={} durationMs={}",
                serviceName, repo.fullName(), ex.getMessage(), System.currentTimeMillis() - started, ex);
            return Optional.empty();
        }
    }

    private static List<String> collectSourcePatches(AnalysisResult analysis, Map<String, String> filesToCommit) {
        List<String> patchedPaths = new ArrayList<>();
        if (analysis.getSuggestedFixes() == null) {
            return patchedPaths;
        }
        for (SuggestedFix fix : analysis.getSuggestedFixes()) {
            if (!StringUtils.hasText(fix.getFilePath()) || !StringUtils.hasText(fix.getNewContent())) {
                continue;
            }
            String path = fix.getFilePath().trim().replace('\\', '/');
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.contains("..") || path.startsWith(".git/")) {
                continue;
            }
            filesToCommit.put(path, fix.getNewContent());
            patchedPaths.add(path);
        }
        return patchedPaths;
    }

    private static List<String> parseReviewers(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
    }

    private static String sanitizeBranch(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]+", "-");
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(0, 200);
        }
        return cleaned;
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    private static String buildRemediationMarkdown(UUID projectId, String service, AnalysisResult analysis, String fingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LogSpectra remediation brief\n\n");
        sb.append("- **projectId**: `").append(projectId).append("`\n");
        sb.append("- **service**: `").append(service).append("`\n");
        sb.append("- **fingerprint**: `").append(fingerprint).append("`\n");
        sb.append("- **severity**: ").append(nullToEmpty(analysis.getSeverity())).append("\n");
        sb.append("- **analyzedAt**: ").append(analysis.getAnalyzedAt() != null ? analysis.getAnalyzedAt() : Instant.now()).append("\n\n");
        sb.append("## Error summary\n\n").append(nullToEmpty(analysis.getErrorSummary())).append("\n\n");
        sb.append("## Root cause\n\n").append(nullToEmpty(analysis.getRootCause())).append("\n\n");
        sb.append("## Suggested fixes\n\n");
        List<SuggestedFix> fixes = analysis.getSuggestedFixes();
        if (fixes == null || fixes.isEmpty()) {
            sb.append("_No structured fixes returned by the model._\n");
        } else {
            for (SuggestedFix fix : fixes) {
                sb.append("- **").append(nullToEmpty(fix.getPriority())).append("**")
                    .append(" (effort=").append(nullToEmpty(fix.getEffort())).append(")");
                if (StringUtils.hasText(fix.getFilePath())) {
                    sb.append(" `").append(fix.getFilePath()).append("`");
                }
                sb.append(": ").append(nullToEmpty(fix.getFix())).append("\n");
            }
        }
        sb.append("\n---\n_Generated by LogSpectra (stateless GitHub Contents / Git Data API)._\n");
        return sb.toString();
    }

    private static String buildPullRequestBody(String service, AnalysisResult analysis, String path,
                                               String fingerprint, List<String> patchedPaths) {
        String fixes = analysis.getSuggestedFixes() == null || analysis.getSuggestedFixes().isEmpty()
            ? "_None_"
            : analysis.getSuggestedFixes().stream()
                .map(f -> "- [" + nullToEmpty(f.getPriority()) + "] "
                    + (StringUtils.hasText(f.getFilePath()) ? "`" + f.getFilePath() + "` — " : "")
                    + nullToEmpty(f.getFix()))
                .collect(Collectors.joining("\n"));

        String filesSection = patchedPaths.isEmpty()
            ? "This PR adds `" + path + "` documenting the analysis. No source file patches were produced by the model."
            : "This PR updates:\n" + patchedPaths.stream().map(p -> "- `" + p + "`").collect(Collectors.joining("\n"))
                + "\n\nPlus brief: `" + path + "`";

        return """
            ## LogSpectra automated remediation

            Service: `%s`
            Fingerprint: `%s`

            ### Summary
            %s

            ### Root cause
            %s

            ### Suggested fixes
            %s

            ### Changes
            %s

            ### Review
            1. Confirm root cause and inspect the source diffs
            2. Adjust the patch if needed
            3. Approve (LGTM) once CI is green
            4. Merge when ready

            <!-- logspectra-fingerprint:%s -->
            """.formatted(
            service,
            fingerprint,
            nullToEmpty(analysis.getErrorSummary()),
            nullToEmpty(analysis.getRootCause()),
            fixes,
            filesSection,
            fingerprint
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
