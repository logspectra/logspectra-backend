package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.loganalyzer.api.dto.request.auth.ChangePasswordRequest;
import com.loganalyzer.api.dto.request.auth.CreateUserRequest;
import com.loganalyzer.api.dto.response.auth.UserResponse;
import com.loganalyzer.domain.model.Role;
import com.loganalyzer.domain.model.User;
import com.loganalyzer.domain.repository.UserRepository;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
            .id(userId)
            .username("testuser")
            .password("encoded-password")
            .role(com.loganalyzer.domain.model.Role.USER)
            .active(true)
            .build();
    }

    @Test
    void createUserShouldPersistNewUser() {
        CreateUserRequest request = new CreateUserRequest("newuser", "password123", Role.USER, true);

        User newUser = User.builder()
            .username("newuser")
            .password("encoded-password123")
            .role(com.loganalyzer.domain.model.Role.USER)
            .active(true)
            .build();

        when(passwordEncoder.encode("password123")).thenReturn("encoded-password123");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals("newuser", response.username());
        assertEquals(Role.USER, response.role());
        assertTrue(response.active());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void listUsersShouldReturnAllUsers() {
        User user2 = User.builder()
            .id(UUID.randomUUID())
            .username("user2")
            .password("encoded")
            .role(com.loganalyzer.domain.model.Role.ADMIN)
            .active(true)
            .build();

        when(userRepository.findAll()).thenReturn(List.of(testUser, user2));

        List<UserResponse> responses = userService.listUsers();

        assertEquals(2, responses.size());
        assertEquals("testuser", responses.get(0).username());
        assertEquals("user2", responses.get(1).username());
    }

    @Test
    void deleteUserShouldRemoveUser() {
        when(userRepository.existsById(userId)).thenReturn(true);

        userService.deleteUser(userId);

        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteUserShouldThrowWhenUserNotFound() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser(userId));
    }

    @Test
    void changePasswordShouldUpdateUserPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("password123", "newpassword123");

        when(userRepository.findByUsername("testuser"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "encoded-password"))
            .thenReturn(true);
        when(passwordEncoder.encode("newpassword123"))
            .thenReturn("encoded-newpassword123");

        userService.changePassword("testuser", request);

        assertEquals("encoded-newpassword123", testUser.getPassword());
    }

    @Test
    void changePasswordShouldThrowWhenOldPasswordIncorrect() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrongpassword", "newpassword123");

        when(userRepository.findByUsername("testuser"))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "encoded-password"))
            .thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
            userService.changePassword("testuser", request)
        );
    }
}

