package com.loganalyzer.service;

import com.loganalyzer.api.dto.request.auth.LoginRequest;
import com.loganalyzer.api.dto.response.auth.LoginResponse;
import com.loganalyzer.api.dto.response.auth.UserResponse;
import com.loganalyzer.domain.model.User;
import com.loganalyzer.domain.repository.UserRepository;
import com.loganalyzer.security.JwtService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            String token = jwtService.generateToken(user.getUsername(), Map.of(
                "role", user.getRole().name(),
                "userId", user.getId().toString()
            ));
            UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
            );
            return new LoginResponse(token, userResponse);
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BadCredentialsException("Invalid credentials", ex);
        }
    }
}
