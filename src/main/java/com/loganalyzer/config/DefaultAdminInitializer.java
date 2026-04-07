package com.loganalyzer.config;

import com.loganalyzer.domain.model.Role;
import com.loganalyzer.domain.model.User;
import com.loganalyzer.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DefaultAdminInitializer {

    @Bean
    ApplicationRunner seedDefaultAdmin(UserRepository userRepository,
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            if (!userRepository.existsByUsername("admin")) {
                userRepository.save(User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin"))
                        .role(Role.ADMIN)
                        .active(true)
                        .build());
                log.info("Default admin user created");
            }
        };
    }
}
