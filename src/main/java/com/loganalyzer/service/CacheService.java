package com.loganalyzer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.api.dto.request.LogSearchRequest;
import com.loganalyzer.domain.model.AISettings;
import com.loganalyzer.api.dto.response.PagedResponse;
import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.api.dto.response.IssueGroupResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private static final String ANALYSIS_PREFIX = "analysis:";
    private static final String SEARCH_PREFIX = "search:";
    private static final String ISSUES_V2_PREFIX = "issues:v2:";
    private static final String ACTIVE_AI_SETTINGS_KEY = "ai:settings:active";
    private static final Duration ISSUES_TTL = Duration.ofMinutes(2);
    private static final TypeReference<List<IssueGroupResponse>> ISSUE_GROUP_LIST_TYPE = new TypeReference<>() {
    };

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void storeAnalysis(String projectId, String fingerprint, AnalysisResult result, Duration ttl) {
        redisTemplate.opsForValue().set(ANALYSIS_PREFIX + projectId + ":" + fingerprint, result, ttl);
    }

    public Optional<AnalysisResult> getAnalysis(String projectId, String fingerprint) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(ANALYSIS_PREFIX + projectId + ":" + fingerprint)).filter(AnalysisResult.class::isInstance).map(AnalysisResult.class::cast);
    }

    public void storeSearchResult(String projectId, LogSearchRequest request, PagedResponse<?> result) {
        redisTemplate.opsForValue().set(SEARCH_PREFIX + projectId + ":" + request.hashCode(), result, Duration.ofSeconds(30));
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<PagedResponse<T>> getSearchResult(String projectId, LogSearchRequest request) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(SEARCH_PREFIX + projectId + ":" + request.hashCode())).map(value -> (PagedResponse<T>) value);
    }


    public void storeActiveAISettings(AISettings settings, Duration ttl) {
        redisTemplate.opsForValue().set(ACTIVE_AI_SETTINGS_KEY, settings, ttl);
    }

    public Optional<AISettings> getActiveAISettings() {
        return Optional.ofNullable(redisTemplate.opsForValue().get(ACTIVE_AI_SETTINGS_KEY))
            .filter(AISettings.class::isInstance)
            .map(AISettings.class::cast);
    }

    public void evictActiveAISettings() {
        redisTemplate.delete(ACTIVE_AI_SETTINGS_KEY);
    }

    public void storeIssues(String projectId, String key, List<IssueGroupResponse> issues) {
        String redisKey = buildIssuesKey(projectId, key);
        try {
            String payload = objectMapper.writeValueAsString(issues);
            redisTemplate.opsForValue().set(redisKey, payload, ISSUES_TTL);
        } catch (Exception e) {
            log.warn("Failed to serialize issues for Redis cache for project: {} key: {}", projectId, key, e);
        }
    }

    public Optional<List<IssueGroupResponse>> getIssues(String projectId, String key) {
        String redisKey = buildIssuesKey(projectId, key);
        try {
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (cached == null) {
                return Optional.empty();
            }
            if (cached instanceof String json) {
                return Optional.of(objectMapper.readValue(json, ISSUE_GROUP_LIST_TYPE));
            }
            return Optional.of(objectMapper.convertValue(cached, ISSUE_GROUP_LIST_TYPE));
        } catch (Exception e) {
            log.warn("Failed to deserialize issues from Redis cache for project: {} key: {}, will reload from database", projectId, key, e);
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception delException) {
                log.debug("Failed to delete corrupted cache entry", delException);
            }
            return Optional.empty();
        }
    }

    private String buildIssuesKey(String projectId, String key) {
        return ISSUES_V2_PREFIX + projectId + ":" + key;
    }
}
