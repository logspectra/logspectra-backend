package com.loganalyzer.api.dto.response.auth;

public record LoginResponse(String token, UserResponse user) {
}
