package com.loganalyzer.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Patterns for browser origins (UI on :3000 dev, :3050 Docker).
     */
    private List<String> allowedOriginPatterns = List.of(
        "http://localhost:*",
        "http://127.0.0.1:*",
        "*"
    );

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }
}
