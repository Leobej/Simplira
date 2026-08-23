package io.github.leobej.infrastructure.config;

import io.github.leobej.domain.port.AccessTokenIssuer;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.RefreshTokenRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.domain.repository.WorkspaceRepository;
import io.github.leobej.domain.service.AuthService;
import io.github.leobej.domain.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Domain services stay framework-free, so their beans are declared here instead of
// being component-scanned.
@Configuration
public class DomainServiceConfig {

    @Bean
    public AuthService authService(UserRepository userRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   PasswordHasher passwordHasher,
                                   AccessTokenIssuer accessTokenIssuer,
                                   @Value("${simplira.jwt.refresh-token-expiry}") long refreshTokenExpirySeconds) {
        return new AuthService(userRepository, refreshTokenRepository, passwordHasher,
                accessTokenIssuer, Duration.ofSeconds(refreshTokenExpirySeconds));
    }

    @Bean
    public WorkspaceService workspaceService(WorkspaceRepository workspaceRepository,
                                             UserWorkspaceRepository userWorkspaceRepository,
                                             UserRepository userRepository) {
        return new WorkspaceService(workspaceRepository, userWorkspaceRepository, userRepository);
    }
}
