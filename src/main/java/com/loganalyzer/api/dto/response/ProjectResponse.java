package com.loganalyzer.api.dto.response;

import com.loganalyzer.domain.model.UserProjectRole;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {
    private UUID id;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private UserProjectRole role;
    private int serviceCount;
}
