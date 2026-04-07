package com.loganalyzer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateProjectRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 5000)
    private String description;
}
