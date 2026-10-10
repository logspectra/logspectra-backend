package com.loganalyzer.ai.agent;

import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.github.GitHubAppProperties;
import com.loganalyzer.github.GitHubRepoRef;
import com.loganalyzer.service.ServiceCodeRegistryService;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class GitHubAgentSessionFactory {

    private final ServiceCodeRegistryService codeRegistryService;
    private final GitHubAppProperties properties;

    public Optional<AgentSession> create(List<LogDocument> logs) {
        if (logs == null || logs.isEmpty()) {
            return Optional.empty();
        }
        if (!properties.getApp().isEnabled() || !properties.getAgent().isToolCallingEnabled()) {
            return Optional.empty();
        }

        String service = logs.stream()
            .map(LogDocument::getService)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
        String projectId = logs.stream()
            .map(LogDocument::getProjectId)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
        if (!StringUtils.hasText(service) || !StringUtils.hasText(projectId)) {
            return Optional.empty();
        }

        Optional<ServiceSourceMapping> mapping = codeRegistryService.find(projectId, service);
        if (mapping.isEmpty() || !StringUtils.hasText(mapping.get().getGitUrl())) {
            return Optional.empty();
        }
        Optional<GitHubRepoRef> repo = GitHubRepoRef.parse(mapping.get().getGitUrl());
        if (repo.isEmpty()) {
            return Optional.empty();
        }

        String ref = StringUtils.hasText(mapping.get().getDefaultBranch())
            ? mapping.get().getDefaultBranch()
            : "main";
        return Optional.of(new AgentSession(
            repo.get(),
            ref,
            mapping.get(),
            projectId,
            service,
            properties.getAgent().getMaxTreeEntries(),
            properties.getAgent().getMaxFileChars()
        ));
    }
}
