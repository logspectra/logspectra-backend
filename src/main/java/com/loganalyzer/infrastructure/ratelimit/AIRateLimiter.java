package com.loganalyzer.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loganalyzer.infrastructure.exception.AIQuotaExceededException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AIRateLimiter {

    private final com.google.common.util.concurrent.RateLimiter rateLimiter;
    private final Cache<String, Boolean> recentFingerprints;

    public AIRateLimiter(@Value("${ai.rate-limit.requests-per-minute:20}") double requestsPerMinute,
                         @Value("${ai.rate-limit.burst:5}") long burst) {
        this.rateLimiter = com.google.common.util.concurrent.RateLimiter.create(requestsPerMinute / 60.0d);
        this.recentFingerprints = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(Math.max(burst * 2, 10_000))
            .build();
    }

    public void acquire(String context) {
        if (!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)) {
            throw new AIQuotaExceededException("AI analysis rate limit exceeded for context " + context);
        }
    }

    public boolean isRecentlyAnalyzed(String fingerprint) {
        return recentFingerprints.getIfPresent(fingerprint) != null;
    }

    public void markAnalyzed(String fingerprint) {
        recentFingerprints.put(fingerprint, Boolean.TRUE);
    }
}
