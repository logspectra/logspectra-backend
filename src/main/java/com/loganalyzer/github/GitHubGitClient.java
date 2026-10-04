package com.loganalyzer.github;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Git Data API helpers for creating a branch + file commit + PR without a local clone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubGitClient {

    private final GitHubAppProperties properties;
    private final GitHubAppTokenService tokenService;

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
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.put("mode", "100644");
        entry.put("type", "blob");
        entry.put("sha", blobSha);
        Map<String, Object> body = Map.of(
            "base_tree", baseTreeSha,
            "tree", List.of(entry)
        );
        Map<String, Object> created = post("/repos/{owner}/{repo}/git/trees", body, repo.owner(), repo.repo());
        return String.valueOf(created.get("sha"));
    }

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
