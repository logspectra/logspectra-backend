package com.loganalyzer.ai;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AIAnalysisRequest {
    AIProvider provider;
    String apiKey;
    String model;
    String baseUrl;
    @Deprecated
    String openAiApiKey;
    AIProvider fallbackProvider;
    Map<String, Object> providerSettings;

    public String resolvedApiKey() {
        if (hasText(apiKey)) {
            return apiKey;
        }
        if (hasText(openAiApiKey)) {
            return openAiApiKey;
        }
        return setting("apiKey");
    }

    public String resolvedModel() {
        if (hasText(model)) {
            return model;
        }
        return setting("model");
    }

    public String resolvedBaseUrl() {
        if (hasText(baseUrl)) {
            return baseUrl;
        }
        return setting("baseUrl");
    }

    public String setting(String key) {
        if (providerSettings == null) {
            return null;
        }
        Object value = providerSettings.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
