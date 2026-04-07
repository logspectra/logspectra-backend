package com.loganalyzer.infrastructure.ratelimit;

import com.loganalyzer.infrastructure.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (rateLimited == null) {
            return true;
        }
        String clientKey = resolveClientKey(request, rateLimited.key());
        Long count = stringRedisTemplate.opsForValue().increment(clientKey);
        if (Objects.equals(count, 1L)) {
            stringRedisTemplate.expire(clientKey, Duration.ofSeconds(rateLimited.windowSeconds()));
        }
        if (count != null && count > rateLimited.limit()) {
            throw new TooManyRequestsException("Rate limit exceeded. Max " + rateLimited.limit() + " requests per " + rateLimited.windowSeconds() + " seconds");
        }
        response.addHeader("X-RateLimit-Limit", String.valueOf(rateLimited.limit()));
        response.addHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, rateLimited.limit() - (count == null ? 0 : count.intValue()))));
        return true;
    }

    private String resolveClientKey(HttpServletRequest request, String actionKey) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String client = forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return "ratelimit:" + actionKey + ":" + client;
    }
}
