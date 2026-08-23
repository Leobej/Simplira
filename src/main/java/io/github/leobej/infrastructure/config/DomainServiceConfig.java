package io.github.leobej.infrastructure.config;

import io.github.leobej.domain.port.AccessTokenIssuer;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.RefreshTokenRepository;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.repository.InvitationRepository;
import io.github.leobej.domain.repository.ProjectMemberRepository;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.domain.repository.WorkspaceRepository;
import io.github.leobej.domain.service.AuthService;
import io.github.leobej.domain.service.InvitationService;
import io.github.leobej.domain.service.ProjectAuthorization;
import io.github.leobej.domain.service.ProjectService;
import io.github.leobej.domain.service.WorkspaceAuthorization;
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
    public WorkspaceAuthorization workspaceAuthorization(WorkspaceRepository workspaceRepository,
                                                        UserWorkspaceRepository userWorkspaceRepository) {
        return new WorkspaceAuthorization(workspaceRepository, userWorkspaceRepository);
    }

    @Bean
    public WorkspaceService workspaceService(WorkspaceRepository workspaceRepository,
                                             UserWorkspaceRepository userWorkspaceRepository,
                                             UserRepository userRepository,
                                             WorkspaceAuthorization authorization) {
        return new WorkspaceService(workspaceRepository, userWorkspaceRepository, userRepository, authorization);
    }

    @Bean
    public InvitationService invitationService(InvitationRepository invitationRepository,
                                               UserWorkspaceRepository userWorkspaceRepository,
                                               UserRepository userRepository,
                                               WorkspaceAuthorization authorization,
                                               EmailService emailService,
                                               @Value("${simplira.invitations.expiry}") long invitationExpirySeconds) {
        return new InvitationService(invitationRepository, userWorkspaceRepository, userRepository,
                authorization, emailService, Duration.ofSeconds(invitationExpirySeconds));
    }

    @Bean
    public ProjectAuthorization projectAuthorization(ProjectRepository projectRepository,
                                                     ProjectMemberRepository projectMemberRepository,
                                                     UserWorkspaceRepository userWorkspaceRepository) {
        return new ProjectAuthorization(projectRepository, projectMemberRepository, userWorkspaceRepository);
    }

    @Bean
    public ProjectService projectService(ProjectRepository projectRepository,
                                         ProjectMemberRepository projectMemberRepository,
                                         UserWorkspaceRepository userWorkspaceRepository,
                                         UserRepository userRepository,
                                         WorkspaceAuthorization workspaceAuthorization,
                                         ProjectAuthorization projectAuthorization) {
        return new ProjectService(projectRepository, projectMemberRepository, userWorkspaceRepository,
                userRepository, workspaceAuthorization, projectAuthorization);
    }
}
