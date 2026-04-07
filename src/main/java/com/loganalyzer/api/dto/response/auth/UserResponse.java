package com.loganalyzer.api.dto.response.auth;

import com.loganalyzer.domain.model.Role;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    Role role,
    boolean active,
    Instant createdAt
) {
}
