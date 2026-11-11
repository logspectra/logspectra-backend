package com.loganalyzer.config;

import com.loganalyzer.infrastructure.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public RateLimitConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.ForwardedHeaderFilter> forwardedHeaderFilter() {
        org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.ForwardedHeaderFilter> bean =
            new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new org.springframework.web.filter.ForwardedHeaderFilter());
        bean.setOrder(0);
        return bean;
    }
}
