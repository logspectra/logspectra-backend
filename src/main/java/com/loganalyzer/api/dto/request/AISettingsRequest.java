package com.loganalyzer.api.dto.request;

import com.loganalyzer.ai.AIProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AISettingsRequest {

    @NotNull
    private AIProvider provider;

    private String apiKey;

    @NotBlank
    private String modelName;

    private String baseUrl;

    private Boolean active;
}
