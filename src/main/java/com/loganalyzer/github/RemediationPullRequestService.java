package com.loganalyzer.github;

import com.loganalyzer.ai.patch.SearchReplacePatchApplier;
import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.domain.model.SuggestedFix;
import com.loganalyzer.service.ServiceCodeRegistryService;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final SearchReplacePatchApplier patchApplier;
    private final GitWorkspaceService gitWorkspaceService;

    public boolean isRemediationEnabled() {
        return properties.getApp().isEnabled() && properties.getPr().isEnabled();
    }

    /** @deprecated Debug no longer opens PRs; use {@link com.loganalyzer.service.LogRemediationService}. */
    @Deprecated
    public boolean shouldCreate(boolean requestFlag) {
        return isRemediationEnabled()
            && (requestFlag || properties.getPr().isAutoCreateAfterAnalysis());
    }

    /**
     * Opens a remediation PR with source-file patches only (no repo markdown artifacts).
     * Opens a PR via git workspace (clone/commit/push) when enabled, else GitHub Git Data API.
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

        if (gitWorkspaceService.isActive()) {
            Optional<RemediationPullRequestResult> workspaceResult =
                createForAnalysisViaWorkspace(projectId, serviceName, analysis, mapping.get(), repoOpt.get(), started);
            if (workspaceResult.isPresent()) {
                return workspaceResult;
            }
            log.warn("operation=createRemediationPr feature=git-workspace status=error service={} fallback=api",
                serviceName);
        }

        return createForAnalysisViaGitHubApi(projectId, serviceName, analysis, mapping.get(), repoOpt.get(), started);
    }

    private Optional<RemediationPullRequestResult> createForAnalysisViaGitHubApi(
        UUID projectId,
        String serviceName,
        AnalysisResult analysis,
        ServiceSourceMapping mapping,
        GitHubRepoRef repo,
        long started
    ) {
        String baseBranch = StringUtils.hasText(mapping.getDefaultBranch())
            ? mapping.getDefaultBranch()
            : "main";
        String fingerprint = StringUtils.hasText(analysis.getFingerprint())
            ? analysis.getFingerprint()
            : UUID.randomUUID().toString();
        String shortFp = fingerprint.length() > 8 ? fingerprint.substring(0, 8) : fingerprint;
        String branch = resolveRemediationBranch(serviceName, shortFp);
        boolean reuseBranch = properties.getPr().isReuseBranchPerFingerprint();

        try {
            Optional<String> existingTip = reuseBranch
                ? gitHubGitClient.getBranchTipSha(repo, branch)
                : Optional.empty();
            String parentSha = existingTip.orElseGet(() -> gitHubGitClient.getDefaultBranchSha(repo, baseBranch));
            String baseTree = gitHubGitClient.getCommitTreeSha(repo, parentSha);
            if (existingTip.isPresent()) {
                log.debug("operation=createRemediationPr feature=github-app-pr status=entry branch={} action=reuse existingTip={}",
                    branch, parentSha);
            }

            Map<String, String> filesToCommit = new LinkedHashMap<>();
            List<String> patchedPaths = collectSourcePatches(
                repo, baseBranch, analysis, filesToCommit, Optional.empty());
            if (patchedPaths.isEmpty()) {
                log.warn("operation=createRemediationPr feature=github-app-pr status=error service={} error=noApplicablePatches",
                    serviceName);
                return Optional.empty();
            }

            List<GitHubGitClient.TreeEntry> treeEntries = new ArrayList<>();
            for (Map.Entry<String, String> file : filesToCommit.entrySet()) {
                String blobSha = gitHubGitClient.createBlob(repo, file.getValue());
                treeEntries.add(new GitHubGitClient.TreeEntry(file.getKey(), blobSha));
            }
            String treeSha = gitHubGitClient.createTree(repo, baseTree, treeEntries);

            String message = "fix(logspectra): remediate " + shortFp + " (" + patchedPaths.size() + " file(s))";
            String commitSha = gitHubGitClient.createCommit(
                repo,
                message,
                treeSha,
                parentSha,
                properties.getPr().getCommitAuthorName(),
                properties.getPr().getCommitAuthorEmail()
            );
            if (existingTip.isPresent()) {
                gitHubGitClient.updateBranchRef(repo, branch, commitSha);
            } else {
                gitHubGitClient.createBranchRef(repo, branch, commitSha);
            }

            String title = "[LogSpectra] " + truncate(
                StringUtils.hasText(analysis.getErrorSummary()) ? analysis.getErrorSummary() : "Remediation",
                72
            );
            String prBody = buildPullRequestBody(serviceName, analysis, fingerprint, patchedPaths);
            Optional<Map<String, Object>> existingPr = reuseBranch
                ? gitHubGitClient.findOpenPullRequest(repo, branch)
                : Optional.empty();
            Map<String, Object> pr = existingPr.orElseGet(() ->
                gitHubGitClient.createPullRequest(repo, title, prBody, branch, baseBranch));
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

    private Optional<RemediationPullRequestResult> createForAnalysisViaWorkspace(
        UUID projectId,
        String serviceName,
        AnalysisResult analysis,
        ServiceSourceMapping mapping,
        GitHubRepoRef repo,
        long started
    ) {
        String baseBranch = StringUtils.hasText(mapping.getDefaultBranch()) ? mapping.getDefaultBranch() : "main";
        String fingerprint = StringUtils.hasText(analysis.getFingerprint())
            ? analysis.getFingerprint()
            : UUID.randomUUID().toString();
        String shortFp = fingerprint.length() > 8 ? fingerprint.substring(0, 8) : fingerprint;
        String branch = resolveRemediationBranch(serviceName, shortFp);
        boolean reuseBranch = properties.getPr().isReuseBranchPerFingerprint();

        try {
            Optional<ServiceSourceMapping> synced = gitWorkspaceService.syncAndEnhance(
                projectId.toString(), serviceName, mapping);
            ServiceSourceMapping materialized = synced.orElse(mapping);
            if (!StringUtils.hasText(materialized.getLocalPath())) {
                return Optional.empty();
            }
            Path workspace = Path.of(materialized.getLocalPath());

            Map<String, String> filesToCommit = new LinkedHashMap<>();
            List<String> patchedPaths = collectSourcePatches(
                repo, baseBranch, analysis, filesToCommit, Optional.of(workspace));
            if (patchedPaths.isEmpty()) {
                log.warn("operation=createRemediationPr feature=git-workspace status=error service={} error=noApplicablePatches",
                    serviceName);
                return Optional.empty();
            }

            gitWorkspaceService.checkoutRemediationBranch(workspace, branch, true);
            for (Map.Entry<String, String> entry : filesToCommit.entrySet()) {
                writeWorkspaceFile(workspace, entry.getKey(), entry.getValue());
            }

            String message = "fix(logspectra): remediate " + shortFp + " (" + patchedPaths.size() + " file(s))";
            gitWorkspaceService.commitAll(workspace, message);
            gitWorkspaceService.pushBranch(workspace, branch, mapping.getGitUrl());

            String commitSha = gitHubGitClient.getBranchTipSha(repo, branch).orElse(null);

            String title = "[LogSpectra] " + truncate(
                StringUtils.hasText(analysis.getErrorSummary()) ? analysis.getErrorSummary() : "Remediation",
                72
            );
            String prBody = buildPullRequestBody(serviceName, analysis, fingerprint, patchedPaths);
            Optional<Map<String, Object>> existingPr = reuseBranch
                ? gitHubGitClient.findOpenPullRequest(repo, branch)
                : Optional.empty();
            Map<String, Object> pr = existingPr.orElseGet(() ->
                gitHubGitClient.createPullRequest(repo, title, prBody, branch, baseBranch));
            Integer number = pr.get("number") instanceof Number n ? n.intValue() : null;
            String htmlUrl = pr.get("html_url") != null ? String.valueOf(pr.get("html_url")) : null;

            if (number != null) {
                List<String> reviewers = parseReviewers(properties.getPr().getReviewers());
                try {
                    gitHubGitClient.requestReviewers(repo, number, reviewers);
                } catch (RuntimeException ex) {
                    log.warn("operation=createRemediationPr feature=git-workspace status=error step=requestReviewers error={}",
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

            log.info("operation=createRemediationPr feature=git-workspace status=exit service={} repo={} branch={} pr={} sourceFiles={} durationMs={}",
                serviceName, repo.fullName(), branch, htmlUrl, patchedPaths.size(), System.currentTimeMillis() - started);
            return Optional.of(result);
        } catch (RuntimeException ex) {
            log.error("operation=createRemediationPr feature=git-workspace status=error service={} repo={} error={} durationMs={}",
                serviceName, repo.fullName(), ex.getMessage(), System.currentTimeMillis() - started, ex);
            return Optional.empty();
        }
    }

    private static void writeWorkspaceFile(Path workspace, String relativePath, String content) {
        String path = relativePath.trim().replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.contains("..")) {
            throw new IllegalArgumentException("Invalid path: " + relativePath);
        }
        Path target = workspace.resolve(path).normalize();
        if (!target.startsWith(workspace)) {
            throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write " + relativePath + " in workspace", ex);
        }
    }

    private String resolveRemediationBranch(String serviceName, String shortFp) {
        String core = sanitizeBranch(properties.getPr().getBranchPrefix()
            + "-" + serviceName
            + "-" + shortFp);
        if (properties.getPr().isReuseBranchPerFingerprint()) {
            return core;
        }
        return sanitizeBranch(core + "-" + BRANCH_TS.format(Instant.now()));
    }

    private List<String> collectSourcePatches(GitHubRepoRef repo,
                                              String baseBranch,
                                              AnalysisResult analysis,
                                              Map<String, String> filesToCommit,
                                              Optional<Path> workspaceRoot) {
        List<String> patchedPaths = new ArrayList<>();
        if (analysis.getSuggestedFixes() == null) {
            return patchedPaths;
        }
        for (SuggestedFix fix : analysis.getSuggestedFixes()) {
            if (!StringUtils.hasText(fix.getFilePath())) {
                continue;
            }
            String path = sanitizeRepoPath(fix.getFilePath());
            if (path == null) {
                continue;
            }

            Optional<String> patched = resolvePatchedContent(repo, baseBranch, path, fix, workspaceRoot);
            if (patched.isEmpty()) {
                continue;
            }
            filesToCommit.put(path, patched.get());
            patchedPaths.add(path);
            log.debug("operation=collectSourcePatches feature=search-replace-patch status=exit path={} mode={}",
                path, StringUtils.hasText(fix.getSearch()) ? "searchReplace" : "newContent");
        }
        return patchedPaths;
    }

    private Optional<String> resolvePatchedContent(GitHubRepoRef repo,
                                                   String ref,
                                                   String path,
                                                   SuggestedFix fix,
                                                   Optional<Path> workspaceRoot) {
        if (StringUtils.hasText(fix.getSearch())) {
            Optional<String> current = readCurrentFile(repo, ref, path, workspaceRoot);
            if (current.isEmpty()) {
                log.warn("operation=collectSourcePatches feature=search-replace-patch status=error path={} error=fileNotFound",
                    path);
                return Optional.empty();
            }
            Optional<String> applied = patchApplier.apply(current.get(), fix.getSearch(), fix.getReplace());
            if (applied.isEmpty()) {
                log.warn("operation=collectSourcePatches feature=search-replace-patch status=error path={} error=searchNotUniqueOrMissing",
                    path);
                return Optional.empty();
            }
            return applied;
        }
        if (StringUtils.hasText(fix.getNewContent())) {
            return Optional.of(fix.getNewContent());
        }
        return Optional.empty();
    }

    private Optional<String> readCurrentFile(GitHubRepoRef repo,
                                             String ref,
                                             String path,
                                             Optional<Path> workspaceRoot) {
        if (workspaceRoot.isPresent()) {
            Path file = workspaceRoot.get().resolve(path).normalize();
            if (!file.startsWith(workspaceRoot.get()) || !Files.isRegularFile(file)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException ex) {
                log.warn("operation=readCurrentFile feature=git-workspace status=error path={} error={}",
                    path, ex.getMessage());
                return Optional.empty();
            }
        }
        return gitHubGitClient.getFileContent(repo, path, ref);
    }

    private static String sanitizeRepoPath(String raw) {
        String path = raw.trim().replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!StringUtils.hasText(path) || path.contains("..") || path.startsWith(".git/")) {
            return null;
        }
        return path;
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

    private static String buildPullRequestBody(String service, AnalysisResult analysis,
                                               String fingerprint, List<String> patchedPaths) {
        String fixes = analysis.getSuggestedFixes() == null || analysis.getSuggestedFixes().isEmpty()
            ? "_None_"
            : analysis.getSuggestedFixes().stream()
                .map(f -> "- [" + nullToEmpty(f.getPriority()) + "] "
                    + (StringUtils.hasText(f.getFilePath()) ? "`" + f.getFilePath() + "` — " : "")
                    + nullToEmpty(f.getFix()))
                .collect(Collectors.joining("\n"));

        String filesSection = patchedPaths.stream()
            .map(p -> "- `" + p + "`")
            .collect(Collectors.joining("\n"));

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
