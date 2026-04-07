package com.loganalyzer.api.dto.request.auth;

import com.loganalyzer.domain.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank String password,
    @NotNull Role role,
    Boolean active
) {
}
