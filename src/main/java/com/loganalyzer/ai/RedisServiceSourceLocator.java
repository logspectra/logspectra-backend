package com.loganalyzer.ai;

import com.loganalyzer.github.GitWorkspaceService;
import com.loganalyzer.service.ServiceCodeRegistryService;
import com.logspectra.ai.codecontext.ServiceSourceLocator;
import com.logspectra.ai.codecontext.ServiceSourceRegistry;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Prefer Redis registrations from client starters; fall back to YAML mappings.
 * When git workspace is enabled, materializes {@code localPath} under the workspace root.
 */
@Component
@Primary
@RequiredArgsConstructor
public class RedisServiceSourceLocator implements ServiceSourceLocator {

    private final ServiceCodeRegistryService registryService;
    private final ServiceSourceRegistry yamlFallback;
    private final GitWorkspaceService gitWorkspaceService;

    @Override
    public Optional<ServiceSourceMapping> find(String projectId, String serviceName) {
        Optional<ServiceSourceMapping> mapping = registryService.find(projectId, serviceName);
        if (mapping.isEmpty()) {
            mapping = yamlFallback.find(projectId, serviceName);
        }
        if (mapping.isEmpty() || !gitWorkspaceService.isActive()) {
            return mapping;
        }
        return gitWorkspaceService.syncAndEnhance(projectId, serviceName, mapping.get());
    }
}
