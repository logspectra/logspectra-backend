package com.loganalyzer.config;

import com.loganalyzer.infrastructure.ratelimit.RateLimitInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final List<String> allowedOriginPatterns;

    public RateLimitConfig(RateLimitInterceptor rateLimitInterceptor,
                           @Value("${app.cors.allowed-origin-patterns:*}") List<String> allowedOriginPatterns) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.ForwardedHeaderFilter> forwardedHeaderFilter() {
        org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.ForwardedHeaderFilter> bean =
            new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new org.springframework.web.filter.ForwardedHeaderFilter());
        bean.setOrder(0);
        return bean;
    }
}
