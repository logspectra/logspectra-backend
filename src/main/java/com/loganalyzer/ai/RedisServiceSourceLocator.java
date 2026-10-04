package com.loganalyzer.ai;

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
 */
@Component
@Primary
@RequiredArgsConstructor
public class RedisServiceSourceLocator implements ServiceSourceLocator {

    private final ServiceCodeRegistryService registryService;
    private final ServiceSourceRegistry yamlFallback;

    @Override
    public Optional<ServiceSourceMapping> find(String projectId, String serviceName) {
        Optional<ServiceSourceMapping> fromRedis = registryService.find(projectId, serviceName);
        if (fromRedis.isPresent()) {
            return fromRedis;
        }
        return yamlFallback.find(projectId, serviceName);
    }
}
