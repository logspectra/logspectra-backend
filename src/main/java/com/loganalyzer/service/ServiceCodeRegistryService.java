package com.loganalyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.api.dto.request.ServiceCodeRegistryRequest;
import com.loganalyzer.api.dto.response.ServiceCodeRegistryResponse;
import com.loganalyzer.github.GitWorkspaceService;
import com.logspectra.ai.model.ServiceSourceMapping;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceCodeRegistryService {

    public static final String KEY_PREFIX = "logspectra:code-registry:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final GitWorkspaceService gitWorkspaceService;

    public ServiceCodeRegistryResponse register(ServiceCodeRegistryRequest request) {
        if (!StringUtils.hasText(request.getGitUrl()) && !StringUtils.hasText(request.getLocalPath())) {
            throw new IllegalArgumentException("gitUrl or localPath is required");
        }

        Instant now = Instant.now();
        List<String> roots = (request.getSourceRoots() == null || request.getSourceRoots().isEmpty())
            ? List.of("src/main/java")
            : request.getSourceRoots();

        StoredMapping stored = new StoredMapping(
            request.getProjectId().trim(),
            request.getServiceName().trim(),
            blankToNull(request.getGitUrl()),
            blankToNull(request.getLocalPath()),
            StringUtils.hasText(request.getDefaultBranch()) ? request.getDefaultBranch().trim() : "main",
            roots,
            now.toString()
        );

        String key = redisKey(stored.projectId(), stored.serviceName());
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(stored), TTL);
            log.info("operation=registerCodeSource feature=logspectra-ai status=exit projectId={} service={} gitUrl={} key={}",
                stored.projectId(), stored.serviceName(), stored.gitUrl(), key);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize code registry payload", ex);
        }

        if (gitWorkspaceService.isActive() && StringUtils.hasText(stored.gitUrl())) {
            ServiceSourceMapping mapping = ServiceSourceMapping.builder()
                .serviceName(stored.serviceName())
                .gitUrl(stored.gitUrl())
                .localPath(stored.localPath())
                .defaultBranch(stored.defaultBranch())
                .sourceRoots(stored.sourceRoots())
                .build();
            gitWorkspaceService.syncAndEnhance(stored.projectId(), stored.serviceName(), mapping);
        }

        return ServiceCodeRegistryResponse.builder()
            .projectId(stored.projectId())
            .serviceName(stored.serviceName())
            .gitUrl(stored.gitUrl())
            .localPath(stored.localPath())
            .defaultBranch(stored.defaultBranch())
            .sourceRoots(stored.sourceRoots())
            .registeredAt(now)
            .redisKey(key)
            .build();
    }

    public Optional<ServiceSourceMapping> find(String projectId, String serviceName) {
        if (!StringUtils.hasText(serviceName)) {
            return Optional.empty();
        }
        if (StringUtils.hasText(projectId)) {
            Optional<ServiceSourceMapping> byProject = read(redisKey(projectId, serviceName));
            if (byProject.isPresent()) {
                return byProject;
            }
        }
        // Fallback: scan is expensive; also try service-only key for older entries — not used.
        return Optional.empty();
    }

    private Optional<ServiceSourceMapping> read(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            StoredMapping stored = objectMapper.readValue(json, StoredMapping.class);
            return Optional.of(ServiceSourceMapping.builder()
                .serviceName(stored.serviceName())
                .gitUrl(stored.gitUrl())
                .localPath(stored.localPath())
                .defaultBranch(stored.defaultBranch())
                .sourceRoots(stored.sourceRoots())
                .build());
        } catch (JsonProcessingException ex) {
            log.warn("operation=findCodeSource feature=logspectra-ai status=error key={} error={}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    public static String redisKey(String projectId, String serviceName) {
        return KEY_PREFIX + projectId.trim() + ":" + serviceName.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record StoredMapping(
        String projectId,
        String serviceName,
        String gitUrl,
        String localPath,
        String defaultBranch,
        List<String> sourceRoots,
        String registeredAt
    ) {}
}
