package com.loganalyzer.api.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank String newPassword
) {
}
