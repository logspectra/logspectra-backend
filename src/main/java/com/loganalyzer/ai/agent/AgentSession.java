package com.loganalyzer.ai.agent;

import com.loganalyzer.github.GitHubRepoRef;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * Mutable per-analysis session: repo identity + cached tree + files already fetched.
 */
@Getter
public class AgentSession {

    private final GitHubRepoRef repo;
    private final String ref;
    private final ServiceSourceMapping mapping;
    private final String projectId;
    private final String serviceName;
    private final int maxTreeEntries;
    private final int maxFileChars;

    private List<String> cachedTree = List.of();
    private final Set<String> fetchedPaths = new LinkedHashSet<>();
    private final List<String> toolTrace = new ArrayList<>();

    public AgentSession(GitHubRepoRef repo,
                        String ref,
                        ServiceSourceMapping mapping,
                        String projectId,
                        String serviceName,
                        int maxTreeEntries,
                        int maxFileChars) {
        this.repo = repo;
        this.ref = ref;
        this.mapping = mapping;
        this.projectId = projectId;
        this.serviceName = serviceName;
        this.maxTreeEntries = maxTreeEntries;
        this.maxFileChars = maxFileChars;
    }

    public void setCachedTree(List<String> paths) {
        this.cachedTree = paths == null ? List.of() : List.copyOf(paths);
    }

    public List<String> getCachedTree() {
        return cachedTree;
    }

    public void markFetched(String path) {
        if (path != null && !path.isBlank()) {
            fetchedPaths.add(path);
        }
    }

    public Set<String> getFetchedPaths() {
        return Collections.unmodifiableSet(fetchedPaths);
    }

    public void recordTrace(String line) {
        toolTrace.add(line);
    }
}
