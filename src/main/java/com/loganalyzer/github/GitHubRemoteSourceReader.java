package com.loganalyzer.github;

import com.logspectra.ai.codecontext.RemoteSourceReader;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Implements {@link RemoteSourceReader} using GitHub Contents API (stateless, no clone).
 */
@Component
@ConditionalOnProperty(prefix = "github.app", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class GitHubRemoteSourceReader implements RemoteSourceReader {

    private final GitHubGitClient gitHubGitClient;

    @Override
    public Optional<String> readFile(ServiceSourceMapping mapping, String relativePath) {
        if (mapping == null || !StringUtils.hasText(mapping.getGitUrl()) || !StringUtils.hasText(relativePath)) {
            return Optional.empty();
        }
        Optional<GitHubRepoRef> repo = GitHubRepoRef.parse(mapping.getGitUrl());
        if (repo.isEmpty()) {
            log.debug("operation=remoteReadFile feature=github-agent status=error error=notGitHubUrl gitUrl={}",
                mapping.getGitUrl());
            return Optional.empty();
        }
        String ref = StringUtils.hasText(mapping.getDefaultBranch()) ? mapping.getDefaultBranch() : "main";
        return gitHubGitClient.getFileContent(repo.get(), relativePath, ref);
    }
}
