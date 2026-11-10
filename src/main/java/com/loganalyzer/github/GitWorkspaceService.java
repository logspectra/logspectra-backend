package com.loganalyzer.github;

import com.logspectra.ai.model.ServiceSourceMapping;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Clone/pull service repos to {@code {root}/{projectId}/{service}} for disk-based analysis and git push remediation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitWorkspaceService {

    private final GitHubAppProperties properties;
    private final GitHubAppTokenService tokenService;
    private final GitCliRunner gitCliRunner;

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public boolean isActive() {
        return properties.getApp().isEnabled()
            && properties.getWorkspace().isEnabled();
    }

    public Path workspacePath(String projectId, String serviceName) {
        String rootConfig = properties.getWorkspace().getRoot();
        if (!StringUtils.hasText(rootConfig)) {
            rootConfig = System.getProperty("user.home") + "/logspectra/code-registry";
        }
        Path root = Path.of(expandHome(rootConfig)).toAbsolutePath().normalize();
        String safeProject = sanitizeSegment(projectId);
        String safeService = sanitizeSegment(serviceName);
        Path resolved = root.resolve(safeProject).resolve(safeService).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid workspace path for service=" + serviceName);
        }
        return resolved;
    }

    /**
     * Ensures a fresh checkout on {@code defaultBranch} and returns mapping with {@code localPath} set to workspace.
     */
    public Optional<ServiceSourceMapping> syncAndEnhance(String projectId,
                                                         String serviceName,
                                                         ServiceSourceMapping mapping) {
        if (!isActive() || mapping == null || !StringUtils.hasText(mapping.getGitUrl())) {
            return Optional.ofNullable(mapping);
        }
        if (!StringUtils.hasText(projectId)) {
            return Optional.of(mapping);
        }
        String branch = StringUtils.hasText(mapping.getDefaultBranch()) ? mapping.getDefaultBranch() : "main";
        Path dir = workspacePath(projectId, serviceName);
        String lockKey = projectId + ":" + serviceName.trim().toLowerCase();
        Object lock = locks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(dir.getParent());
                Duration timeout = Duration.ofSeconds(Math.max(30, properties.getWorkspace().getCommandTimeoutSeconds()));
                String cloneUrl = authenticatedCloneUrl(mapping.getGitUrl());
                if (!Files.isDirectory(dir.resolve(".git"))) {
                    if (Files.exists(dir)) {
                        deleteRecursively(dir);
                    }
                    int depth = Math.max(1, properties.getWorkspace().getCloneDepth());
                    log.info("operation=gitWorkspace feature=git-workspace status=entry action=clone projectId={} service={} path={}",
                        projectId, serviceName, dir);
                    gitCliRunner.run(null, timeout,
                        "clone", "--depth", String.valueOf(depth), "--branch", branch, cloneUrl, dir.toString());
                } else {
                    log.debug("operation=gitWorkspace feature=git-workspace status=entry action=sync projectId={} service={}",
                        projectId, serviceName);
                    gitCliRunner.run(dir, timeout, "fetch", "origin", branch);
                    gitCliRunner.run(dir, timeout, "checkout", branch);
                    gitCliRunner.run(dir, timeout, "reset", "--hard", "origin/" + branch);
                }
                configureIdentity(dir, timeout);
                return Optional.of(ServiceSourceMapping.builder()
                    .serviceName(mapping.getServiceName())
                    .gitUrl(mapping.getGitUrl())
                    .localPath(dir.toString())
                    .defaultBranch(branch)
                    .sourceRoots(mapping.getSourceRoots())
                    .build());
            } catch (IOException | RuntimeException ex) {
                log.error("operation=gitWorkspace feature=git-workspace status=error projectId={} service={} error={}",
                    projectId, serviceName, ex.getMessage());
                return Optional.of(mapping);
            }
        }
    }

    /**
     * Checkout remediation branch from current HEAD (workspace should already be synced on base).
     */
    public void checkoutRemediationBranch(Path workspaceDir, String branch, boolean create) {
        Duration timeout = Duration.ofSeconds(properties.getWorkspace().getCommandTimeoutSeconds());
        if (create) {
            gitCliRunner.run(workspaceDir, timeout, "checkout", "-B", branch);
        } else {
            gitCliRunner.run(workspaceDir, timeout, "checkout", branch);
        }
    }

    public void commitAll(Path workspaceDir, String message) {
        Duration timeout = Duration.ofSeconds(properties.getWorkspace().getCommandTimeoutSeconds());
        gitCliRunner.run(workspaceDir, timeout, "add", "-A");
        try {
            gitCliRunner.run(workspaceDir, timeout, "commit", "-m", message);
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("nothing to commit")) {
                log.debug("operation=gitWorkspace feature=git-workspace status=skip reason=nothingToCommit");
                return;
            }
            throw ex;
        }
    }

    public void pushBranch(Path workspaceDir, String branch, String gitUrl) {
        Duration timeout = Duration.ofSeconds(properties.getWorkspace().getCommandTimeoutSeconds());
        String url = authenticatedCloneUrl(gitUrl);
        gitCliRunner.run(workspaceDir, timeout, "push", url, "HEAD:refs/heads/" + branch);
    }

    private void configureIdentity(Path dir, Duration timeout) {
        String name = properties.getPr().getCommitAuthorName();
        String email = properties.getPr().getCommitAuthorEmail();
        gitCliRunner.run(dir, timeout, "config", "user.name", name);
        gitCliRunner.run(dir, timeout, "config", "user.email", email);
    }

    String authenticatedCloneUrl(String gitUrl) {
        Optional<GitHubRepoRef> repo = GitHubRepoRef.parse(gitUrl);
        if (repo.isEmpty()) {
            throw new IllegalArgumentException("Workspace requires a github.com gitUrl: " + gitUrl);
        }
        String token = tokenService.getInstallationToken();
        return "https://x-access-token:" + token + "@github.com/" + repo.get().fullName() + ".git";
    }

    private static String expandHome(String path) {
        if (path == null) {
            return System.getProperty("user.home") + "/logspectra/code-registry";
        }
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private static String sanitizeSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "_";
        }
        return value.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    for (Path child : stream.toList()) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to clear workspace path " + path, ex);
        }
    }
}
