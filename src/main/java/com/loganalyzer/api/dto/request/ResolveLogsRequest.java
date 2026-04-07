package com.loganalyzer.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveLogsRequest {
    @NotEmpty(message = "logIds cannot be empty")
    private List<String> logIds;
}

