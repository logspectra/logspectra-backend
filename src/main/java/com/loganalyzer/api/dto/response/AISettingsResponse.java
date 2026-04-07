package com.loganalyzer.api.dto.response;

import com.loganalyzer.ai.AIProvider;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AISettingsResponse {
    private UUID id;
    private AIProvider provider;
    private String apiKey;
    private String modelName;
    private String baseUrl;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
