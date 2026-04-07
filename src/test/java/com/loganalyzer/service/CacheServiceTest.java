package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.api.dto.response.IssueGroupResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    private static final String PROJECT_ID = "project-1";
    private static final String QUERY_KEY = "24h:null:0:100";
    private static final String ISSUES_KEY = "issues:v2:" + PROJECT_ID + ":" + QUERY_KEY;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheService cacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cacheService = new CacheService(redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void storeIssuesShouldPersistJsonPayloadWithVersionedKey() throws Exception {
        List<IssueGroupResponse> issues = List.of(IssueGroupResponse.builder().service("api").title("timeout").build());

        cacheService.storeIssues(PROJECT_ID, QUERY_KEY, issues);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(eq(ISSUES_KEY), payloadCaptor.capture(), eq(Duration.ofMinutes(2)));

        String payload = (String) payloadCaptor.getValue();
        List<IssueGroupResponse> restored = objectMapper.readValue(payload, new TypeReference<>() {
        });
        assertEquals(1, restored.size());
        assertEquals("api", restored.getFirst().getService());
    }

    @Test
    void getIssuesShouldReturnPayloadFromVersionedKey() throws Exception {
        List<IssueGroupResponse> issues = List.of(IssueGroupResponse.builder().service("auth").title("null pointer").build());
        when(valueOperations.get(ISSUES_KEY)).thenReturn(objectMapper.writeValueAsString(issues));

        Optional<List<IssueGroupResponse>> result = cacheService.getIssues(PROJECT_ID, QUERY_KEY);

        assertTrue(result.isPresent());
        assertEquals("auth", result.orElseThrow().getFirst().getService());
    }

    @Test
    void getIssuesShouldEvictCorruptedEntryAndReturnEmpty() {
        when(valueOperations.get(ISSUES_KEY)).thenReturn("not-json");

        Optional<List<IssueGroupResponse>> result = cacheService.getIssues(PROJECT_ID, QUERY_KEY);

        assertFalse(result.isPresent());
        verify(redisTemplate).delete(ISSUES_KEY);
    }

    @Test
    void getIssuesShouldReturnEmptyWhenCacheMiss() {
        when(valueOperations.get(ISSUES_KEY)).thenReturn(null);

        Optional<List<IssueGroupResponse>> result = cacheService.getIssues(PROJECT_ID, QUERY_KEY);

        assertFalse(result.isPresent());
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(ISSUES_KEY);
    }
}

