package io.github.leobej.infrastructure.config;

import io.github.leobej.domain.port.AccessTokenIssuer;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.service.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Domain services stay framework-free, so their beans are declared here instead of
// being component-scanned.
@Configuration
public class DomainServiceConfig {

    @Bean
    public AuthService authService(UserRepository userRepository,
                                   PasswordHasher passwordHasher,
                                   AccessTokenIssuer accessTokenIssuer) {
        return new AuthService(userRepository, passwordHasher, accessTokenIssuer);
    }
}
