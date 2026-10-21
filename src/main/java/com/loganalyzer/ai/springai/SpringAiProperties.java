package com.loganalyzer.ai.springai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.spring-ai")
public class SpringAiProperties {

    /**
     * When true, chunked analysis uses Spring AI ChatModel instead of hand-rolled REST clients.
     */
    private boolean enabled = true;
}
