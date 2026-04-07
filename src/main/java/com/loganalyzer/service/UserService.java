package com.loganalyzer.service;

import com.loganalyzer.api.dto.request.auth.ChangePasswordRequest;
import com.loganalyzer.api.dto.request.auth.CreateUserRequest;
import com.loganalyzer.api.dto.response.auth.UserResponse;
import com.loganalyzer.domain.model.User;
import com.loganalyzer.domain.repository.UserRepository;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = User.builder()
            .username(request.username().trim())
            .password(passwordEncoder.encode(request.password()))
            .role(request.role())
            .active(request.active() == null || request.active())
            .build();

        return toResponse(userRepository.save(user));
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new AccessDeniedException("Current password is invalid");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    public UUID findUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
            .map(User::getId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    public boolean isAdmin(UUID userId) {
        return userRepository.findById(userId)
            .map(user -> user.getRole().name().equals("ADMIN"))
            .orElse(false);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
