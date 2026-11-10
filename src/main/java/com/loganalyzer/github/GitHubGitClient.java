package com.loganalyzer.github;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * GitHub REST helpers — Contents API for reads + Git Data API for branch/commit/PR.
 * No local {@code git clone}; all operations are in-memory HTTPS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubGitClient {

    private final GitHubAppProperties properties;
    private final GitHubAppTokenService tokenService;

    /**
     * Read a file via Contents API into memory (base64 decode). Empty when path is missing.
     */
    public Optional<String> getFileContent(GitHubRepoRef repo, String path, String ref) {
        if (!StringUtils.hasText(path)) {
            return Optional.empty();
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> response = client().get()
                .uri(uriBuilder -> {
                    uriBuilder.pathSegment("repos", repo.owner(), repo.repo(), "contents");
                    for (String segment : normalized.split("/")) {
                        if (StringUtils.hasText(segment)) {
                            uriBuilder.pathSegment(segment);
                        }
                    }
                    if (StringUtils.hasText(ref)) {
                        uriBuilder.queryParam("ref", ref);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.get("content") == null) {
                return Optional.empty();
            }
            String encoding = response.get("encoding") != null
                ? String.valueOf(response.get("encoding")) : "base64";
            String raw = String.valueOf(response.get("content")).replaceAll("\\s", "");
            if (!"base64".equalsIgnoreCase(encoding)) {
                return Optional.of(raw);
            }
            String decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
            log.debug("operation=githubGetFile feature=github-agent status=exit path={} durationMs={}",
                normalized, System.currentTimeMillis() - started);
            return Optional.of(decoded);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.debug("operation=githubGetFile feature=github-agent status=exit path={} notFound=true durationMs={}",
                    normalized, System.currentTimeMillis() - started);
                return Optional.empty();
            }
            log.error("operation=githubGetFile feature=github-agent status=error path={} error={} durationMs={}",
                normalized, ex.getMessage(), System.currentTimeMillis() - started, ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("operation=githubGetFile feature=github-agent status=error path={} error={} durationMs={}",
                normalized, ex.getMessage(), System.currentTimeMillis() - started, ex);
            throw ex;
        }
    }

    /**
     * Recursive file paths (blobs only) for a branch tip — used by the iterative agent.
     */
    public List<String> listFilePaths(GitHubRepoRef repo, String branch, int maxEntries) {
        long started = System.currentTimeMillis();
        String commitSha = getDefaultBranchSha(repo, branch);
        String treeSha = getCommitTreeSha(repo, commitSha);
        Map<String, Object> tree = client().get()
            .uri(uriBuilder -> uriBuilder
                .path("/repos/{owner}/{repo}/git/trees/{sha}")
                .queryParam("recursive", "1")
                .build(repo.owner(), repo.repo(), treeSha))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (tree == null) {
            tree = Map.of();
        }
        Object raw = tree.get("tree");
        if (!(raw instanceof List<?> entries)) {
            return List.of();
        }
        int limit = Math.max(1, maxEntries);
        List<String> paths = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object type = map.get("type");
            Object path = map.get("path");
            if (!"blob".equals(type) || path == null) {
                continue;
            }
            paths.add(String.valueOf(path));
            if (paths.size() >= limit) {
                break;
            }
        }
        log.debug("operation=githubListTree feature=github-agent status=exit repo={} files={} truncated={} durationMs={}",
            repo.fullName(), paths.size(), Boolean.TRUE.equals(tree.get("truncated")), System.currentTimeMillis() - started);
        return paths;
    }

    public String getDefaultBranchSha(GitHubRepoRef repo, String branch) {
        Map<String, Object> ref = get("/repos/{owner}/{repo}/git/ref/heads/{branch}", repo.owner(), repo.repo(), branch);
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) ref.get("object");
        if (object == null || object.get("sha") == null) {
            throw new IllegalStateException("Missing sha for branch " + branch + " in " + repo.fullName());
        }
        return String.valueOf(object.get("sha"));
    }

    public String getCommitTreeSha(GitHubRepoRef repo, String commitSha) {
        Map<String, Object> commit = get("/repos/{owner}/{repo}/git/commits/{sha}", repo.owner(), repo.repo(), commitSha);
        @SuppressWarnings("unchecked")
        Map<String, Object> tree = (Map<String, Object>) commit.get("tree");
        if (tree == null || tree.get("sha") == null) {
            throw new IllegalStateException("Missing tree sha for commit " + commitSha);
        }
        return String.valueOf(tree.get("sha"));
    }

    public String createBlob(GitHubRepoRef repo, String contentUtf8) {
        Map<String, Object> body = Map.of(
            "content", Base64.getEncoder().encodeToString(contentUtf8.getBytes(StandardCharsets.UTF_8)),
            "encoding", "base64"
        );
        Map<String, Object> created = post("/repos/{owner}/{repo}/git/blobs", body, repo.owner(), repo.repo());
        return String.valueOf(created.get("sha"));
    }

    public String createTree(GitHubRepoRef repo, String baseTreeSha, String path, String blobSha) {
        return createTree(repo, baseTreeSha, List.of(new TreeEntry(path, blobSha)));
    }

    public String createTree(GitHubRepoRef repo, String baseTreeSha, List<TreeEntry> entries) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (TreeEntry entry : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", entry.path());
            row.put("mode", "100644");
            row.put("type", "blob");
            row.put("sha", entry.blobSha());
            tree.add(row);
        }
        Map<String, Object> body = Map.of(
            "base_tree", baseTreeSha,
            "tree", tree
        );
        Map<String, Object> created = post("/repos/{owner}/{repo}/git/trees", body, repo.owner(), repo.repo());
        return String.valueOf(created.get("sha"));
    }

    public record TreeEntry(String path, String blobSha) {}

    public String createCommit(GitHubRepoRef repo, String message, String treeSha, String parentSha,
                               String authorName, String authorEmail) {
        Map<String, Object> author = Map.of(
            "name", authorName,
            "email", authorEmail
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("tree", treeSha);
        body.put("parents", List.of(parentSha));
        body.put("author", author);
        body.put("committer", author);
        Map<String, Object> created = post("/repos/{owner}/{repo}/git/commits", body, repo.owner(), repo.repo());
        return String.valueOf(created.get("sha"));
    }

    public void createBranchRef(GitHubRepoRef repo, String branch, String commitSha) {
        Map<String, Object> body = Map.of(
            "ref", "refs/heads/" + branch,
            "sha", commitSha
        );
        post("/repos/{owner}/{repo}/git/refs", body, repo.owner(), repo.repo());
    }

    /** Tip commit SHA for {@code refs/heads/<branch>}, empty when the branch does not exist. */
    public Optional<String> getBranchTipSha(GitHubRepoRef repo, String branch) {
        if (!StringUtils.hasText(branch)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> response = get(
                "/repos/{owner}/{repo}/git/ref/heads/{branch}",
                repo.owner(),
                repo.repo(),
                branch
            );
            Object object = response.get("object");
            if (object instanceof Map<?, ?> ref && ref.get("sha") != null) {
                return Optional.of(String.valueOf(ref.get("sha")));
            }
            return Optional.empty();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    public void updateBranchRef(GitHubRepoRef repo, String branch, String commitSha) {
        Map<String, Object> body = Map.of("sha", commitSha, "force", false);
        client().patch()
            .uri("/repos/{owner}/{repo}/git/refs/heads/{branch}", repo.owner(), repo.repo(), branch)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    /** First open PR whose head is {@code owner:branch}, if any. */
    public Optional<Map<String, Object>> findOpenPullRequest(GitHubRepoRef repo, String headBranch) {
        String head = repo.owner() + ":" + headBranch;
        List<Map<String, Object>> pulls = client().get()
            .uri(uriBuilder -> uriBuilder
                .path("/repos/{owner}/{repo}/pulls")
                .queryParam("state", "open")
                .queryParam("head", head)
                .queryParam("per_page", "1")
                .build(repo.owner(), repo.repo()))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (pulls == null || pulls.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pulls.getFirst());
    }

    public Map<String, Object> createPullRequest(GitHubRepoRef repo, String title, String bodyMarkdown,
                                                 String head, String base) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("body", bodyMarkdown);
        body.put("head", head);
        body.put("base", base);
        return post("/repos/{owner}/{repo}/pulls", body, repo.owner(), repo.repo());
    }

    public void requestReviewers(GitHubRepoRef repo, int prNumber, List<String> reviewers) {
        if (reviewers == null || reviewers.isEmpty()) {
            return;
        }
        Map<String, Object> body = Map.of("reviewers", reviewers);
        post("/repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers", body,
            repo.owner(), repo.repo(), prNumber);
    }

    private Map<String, Object> get(String path, Object... uriVars) {
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> response = client().get()
                .uri(path, uriVars)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            log.debug("operation=githubGet feature=github-app-pr status=exit path={} durationMs={}",
                path, System.currentTimeMillis() - started);
            return response == null ? Map.of() : response;
        } catch (RuntimeException ex) {
            log.error("operation=githubGet feature=github-app-pr status=error path={} error={} durationMs={}",
                path, ex.getMessage(), System.currentTimeMillis() - started, ex);
            throw ex;
        }
    }

    private Map<String, Object> post(String path, Object body, Object... uriVars) {
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> response = client().post()
                .uri(path, uriVars)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            log.debug("operation=githubPost feature=github-app-pr status=exit path={} durationMs={}",
                path, System.currentTimeMillis() - started);
            return response == null ? Map.of() : response;
        } catch (RuntimeException ex) {
            log.error("operation=githubPost feature=github-app-pr status=error path={} error={} durationMs={}",
                path, ex.getMessage(), System.currentTimeMillis() - started, ex);
            throw ex;
        }
    }

    private RestClient client() {
        String token = tokenService.getInstallationToken();
        return RestClient.builder()
            .baseUrl(properties.getApp().getApiBaseUrl())
            .defaultHeader("Authorization", "Bearer " + token)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }
}
