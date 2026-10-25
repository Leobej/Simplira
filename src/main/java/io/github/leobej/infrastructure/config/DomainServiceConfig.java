package io.github.leobej.infrastructure.config;

import io.github.leobej.domain.port.AccessTokenIssuer;
import io.github.leobej.domain.port.ContentTypeDetector;
import io.github.leobej.domain.port.FileStorage;
import io.github.leobej.domain.port.PasswordHasher;
import io.github.leobej.domain.repository.AttachmentRepository;
import io.github.leobej.domain.repository.AuditLogRepository;
import io.github.leobej.domain.repository.RefreshTokenRepository;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.port.LiveUpdatePublisher;
import io.github.leobej.domain.port.NotificationPublisher;
import io.github.leobej.domain.repository.CommentRepository;
import io.github.leobej.domain.repository.DefaultIssueStatusRepository;
import io.github.leobej.domain.repository.DefaultIssueTypeRepository;
import io.github.leobej.domain.repository.EmailOutboxRepository;
import io.github.leobej.domain.repository.EmailVerificationTokenRepository;
import io.github.leobej.domain.repository.InvitationRepository;
import io.github.leobej.domain.repository.IssueAssigneeRepository;
import io.github.leobej.domain.repository.IssueLinkRepository;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.IssueStatusRepository;
import io.github.leobej.domain.repository.IssueTypeRepository;
import io.github.leobej.domain.repository.LabelRepository;
import io.github.leobej.domain.repository.NotificationPreferenceRepository;
import io.github.leobej.domain.repository.NotificationRepository;
import io.github.leobej.domain.repository.PasswordResetTokenRepository;
import io.github.leobej.domain.repository.ProjectMemberRepository;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.domain.repository.SprintRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.domain.repository.WorkspaceRepository;
import io.github.leobej.domain.service.AssignmentRevoker;
import io.github.leobej.domain.service.AttachmentService;
import io.github.leobej.domain.service.AuditLogService;
import io.github.leobej.domain.service.AuditTrail;
import io.github.leobej.domain.service.AuthService;
import io.github.leobej.domain.service.BlockedFlagSync;
import io.github.leobej.domain.service.ChartService;
import io.github.leobej.domain.service.CommentService;
import io.github.leobej.domain.service.EmailOutboxDispatcher;
import io.github.leobej.domain.service.EmailVerificationService;
import io.github.leobej.domain.service.InvitationService;
import io.github.leobej.domain.service.IssueAssigneeService;
import io.github.leobej.domain.service.IssueLinkService;
import io.github.leobej.domain.service.IssueService;
import io.github.leobej.domain.service.IssueStatusService;
import io.github.leobej.domain.service.IssueTypeService;
import io.github.leobej.domain.service.LabelService;
import io.github.leobej.domain.service.NotificationService;
import io.github.leobej.domain.service.Notifier;
import io.github.leobej.domain.service.PasswordResetService;
import io.github.leobej.domain.service.ProjectAuthorization;
import io.github.leobej.domain.service.ProjectSetupService;
import io.github.leobej.domain.service.ProjectService;
import io.github.leobej.domain.service.SprintService;
import io.github.leobej.domain.service.UserProfileService;
import io.github.leobej.domain.service.VerifiedEmailGate;
import io.github.leobej.domain.service.WorkspaceAuthorization;
import io.github.leobej.domain.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
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
                                   @Value("${simplira.jwt.refresh-token-expiry}") long refreshTokenExpirySeconds,
                                   EmailVerificationService emailVerificationService) {
        return new AuthService(userRepository, refreshTokenRepository, passwordHasher,
                accessTokenIssuer, Duration.ofSeconds(refreshTokenExpirySeconds), emailVerificationService);
    }

    @Bean
    public EmailVerificationService emailVerificationService(
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            UserRepository userRepository,
            EmailService emailService,
            @Value("${simplira.auth.email-verification.expiry}") long verificationExpirySeconds,
            @Value("${simplira.auth.email-cooldown}") long cooldownSeconds) {
        return new EmailVerificationService(emailVerificationTokenRepository, userRepository, emailService,
                Duration.ofSeconds(verificationExpirySeconds), Duration.ofSeconds(cooldownSeconds));
    }

    @Bean
    public PasswordResetService passwordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            EmailService emailService,
            @Value("${simplira.auth.password-reset.expiry}") long resetExpirySeconds,
            @Value("${simplira.auth.email-cooldown}") long cooldownSeconds) {
        return new PasswordResetService(passwordResetTokenRepository, userRepository, refreshTokenRepository,
                passwordHasher, emailService, Duration.ofSeconds(resetExpirySeconds),
                Duration.ofSeconds(cooldownSeconds));
    }

    @Bean
    public UserProfileService userProfileService(UserRepository userRepository,
                                                 FileStorage fileStorage,
                                                 ContentTypeDetector contentTypeDetector) {
        return new UserProfileService(userRepository, fileStorage, contentTypeDetector);
    }

    @Bean
    public VerifiedEmailGate verifiedEmailGate(UserRepository userRepository,
                                               @Value("${simplira.auth.require-verified-email}") boolean enforced) {
        return new VerifiedEmailGate(userRepository, enforced);
    }

    @Bean
    public WorkspaceAuthorization workspaceAuthorization(WorkspaceRepository workspaceRepository,
                                                        UserWorkspaceRepository userWorkspaceRepository) {
        return new WorkspaceAuthorization(workspaceRepository, userWorkspaceRepository);
    }

    // Every service that changes something worth remembering records it through this - and every
    // record is also announced to open boards.
    @Bean
    public AuditTrail auditTrail(AuditLogRepository auditLogRepository,
                                 ProjectRepository projectRepository,
                                 LiveUpdatePublisher liveUpdatePublisher) {
        return new AuditTrail(auditLogRepository, projectRepository, liveUpdatePublisher);
    }

    @Bean
    public AuditLogService auditLogService(AuditLogRepository auditLogRepository,
                                           IssueRepository issueRepository,
                                           UserRepository userRepository,
                                           WorkspaceAuthorization workspaceAuthorization,
                                           ProjectAuthorization projectAuthorization) {
        return new AuditLogService(auditLogRepository, issueRepository, userRepository, workspaceAuthorization,
                projectAuthorization);
    }

    @Bean
    public WorkspaceService workspaceService(WorkspaceRepository workspaceRepository,
                                             UserWorkspaceRepository userWorkspaceRepository,
                                             UserRepository userRepository,
                                             WorkspaceAuthorization authorization,
                                             AssignmentRevoker assignmentRevoker,
                                             VerifiedEmailGate verifiedEmailGate,
                                             AuditTrail auditTrail) {
        return new WorkspaceService(workspaceRepository, userWorkspaceRepository, userRepository,
                authorization, assignmentRevoker, auditTrail, verifiedEmailGate);
    }

    @Bean
    public InvitationService invitationService(InvitationRepository invitationRepository,
                                               UserWorkspaceRepository userWorkspaceRepository,
                                               UserRepository userRepository,
                                               WorkspaceAuthorization authorization,
                                               EmailService emailService,
                                               @Value("${simplira.invitations.expiry}") long invitationExpirySeconds,
                                               AuditTrail auditTrail,
                                               VerifiedEmailGate verifiedEmailGate) {
        return new InvitationService(invitationRepository, userWorkspaceRepository, userRepository,
                authorization, emailService, Duration.ofSeconds(invitationExpirySeconds), auditTrail,
                verifiedEmailGate);
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
                                         ProjectAuthorization projectAuthorization,
                                         ProjectSetupService projectSetupService,
                                         AssignmentRevoker assignmentRevoker,
                                         AuditTrail auditTrail) {
        return new ProjectService(projectRepository, projectMemberRepository, userWorkspaceRepository,
                userRepository, workspaceAuthorization, projectAuthorization, projectSetupService,
                assignmentRevoker, auditTrail);
    }

    // Every service that changes something people care about tells them through this.
    @Bean
    public Notifier notifier(NotificationRepository notificationRepository,
                             NotificationPreferenceRepository notificationPreferenceRepository,
                             UserRepository userRepository,
                             ProjectRepository projectRepository,
                             IssueAssigneeRepository issueAssigneeRepository,
                             ProjectAuthorization projectAuthorization,
                             EmailOutboxRepository emailOutboxRepository,
                             NotificationPublisher notificationPublisher,
                             @Value("${simplira.notifications.expiry-days}") long expiryDays) {
        return new Notifier(notificationRepository, notificationPreferenceRepository, userRepository,
                projectRepository, issueAssigneeRepository, projectAuthorization, emailOutboxRepository,
                notificationPublisher, Duration.ofDays(expiryDays));
    }

    @Bean
    public EmailOutboxDispatcher emailOutboxDispatcher(EmailOutboxRepository emailOutboxRepository,
                                                       UserRepository userRepository,
                                                       EmailService emailService) {
        return new EmailOutboxDispatcher(emailOutboxRepository, userRepository, emailService);
    }

    @Bean
    public NotificationService notificationService(NotificationRepository notificationRepository,
                                                   NotificationPreferenceRepository notificationPreferenceRepository,
                                                   WorkspaceAuthorization workspaceAuthorization) {
        return new NotificationService(notificationRepository, notificationPreferenceRepository,
                workspaceAuthorization);
    }

    @Bean
    public SprintService sprintService(SprintRepository sprintRepository,
                                       IssueRepository issueRepository,
                                       IssueStatusRepository issueStatusRepository,
                                       ProjectAuthorization projectAuthorization,
                                       Notifier notifier,
                                       AuditTrail auditTrail) {
        return new SprintService(sprintRepository, issueRepository, issueStatusRepository,
                projectAuthorization, notifier, auditTrail);
    }

    @Bean
    public ProjectSetupService projectSetupService(IssueTypeRepository issueTypeRepository,
                                                   IssueStatusRepository issueStatusRepository,
                                                   DefaultIssueTypeRepository defaultIssueTypeRepository,
                                                   DefaultIssueStatusRepository defaultIssueStatusRepository) {
        return new ProjectSetupService(issueTypeRepository, issueStatusRepository,
                defaultIssueTypeRepository, defaultIssueStatusRepository);
    }

    @Bean
    public IssueTypeService issueTypeService(IssueTypeRepository issueTypeRepository,
                                             IssueRepository issueRepository,
                                             ProjectAuthorization projectAuthorization) {
        return new IssueTypeService(issueTypeRepository, issueRepository, projectAuthorization);
    }

    @Bean
    public IssueStatusService issueStatusService(IssueStatusRepository issueStatusRepository,
                                                 IssueRepository issueRepository,
                                                 ProjectAuthorization projectAuthorization) {
        return new IssueStatusService(issueStatusRepository, issueRepository, projectAuthorization);
    }

    @Bean
    public BlockedFlagSync blockedFlagSync(IssueRepository issueRepository,
                                           IssueLinkRepository issueLinkRepository) {
        return new BlockedFlagSync(issueRepository, issueLinkRepository);
    }

    @Bean
    public IssueService issueService(IssueRepository issueRepository,
                                     IssueTypeRepository issueTypeRepository,
                                     IssueStatusRepository issueStatusRepository,
                                     ProjectRepository projectRepository,
                                     SprintRepository sprintRepository,
                                     ProjectAuthorization projectAuthorization,
                                     BlockedFlagSync blockedFlagSync,
                                     Notifier notifier,
                                     AuditTrail auditTrail,
                                     LiveUpdatePublisher liveUpdatePublisher) {
        return new IssueService(issueRepository, issueTypeRepository, issueStatusRepository,
                projectRepository, sprintRepository, projectAuthorization, blockedFlagSync, notifier, auditTrail,
                liveUpdatePublisher);
    }

    @Bean
    public IssueLinkService issueLinkService(IssueLinkRepository issueLinkRepository,
                                             IssueRepository issueRepository,
                                             ProjectAuthorization projectAuthorization,
                                             BlockedFlagSync blockedFlagSync,
                                             Notifier notifier,
                                             AuditTrail auditTrail) {
        return new IssueLinkService(issueLinkRepository, issueRepository, projectAuthorization,
                blockedFlagSync, notifier, auditTrail);
    }

    @Bean
    public IssueAssigneeService issueAssigneeService(IssueAssigneeRepository issueAssigneeRepository,
                                                     IssueRepository issueRepository,
                                                     UserRepository userRepository,
                                                     ProjectAuthorization projectAuthorization,
                                                     Notifier notifier,
                                                     AuditTrail auditTrail) {
        return new IssueAssigneeService(issueAssigneeRepository, issueRepository, userRepository,
                projectAuthorization, notifier, auditTrail);
    }

    @Bean
    public AssignmentRevoker assignmentRevoker(IssueAssigneeRepository issueAssigneeRepository,
                                               ProjectAuthorization projectAuthorization,
                                               AuditTrail auditTrail) {
        return new AssignmentRevoker(issueAssigneeRepository, projectAuthorization, auditTrail);
    }

    @Bean
    public CommentService commentService(CommentRepository commentRepository,
                                         IssueRepository issueRepository,
                                         UserRepository userRepository,
                                         ProjectAuthorization projectAuthorization,
                                         Notifier notifier,
                                         AuditTrail auditTrail) {
        return new CommentService(commentRepository, issueRepository, userRepository, projectAuthorization,
                notifier, auditTrail);
    }

    @Bean
    public AttachmentService attachmentService(AttachmentRepository attachmentRepository,
                                               IssueRepository issueRepository,
                                               ProjectAuthorization projectAuthorization,
                                               FileStorage fileStorage,
                                               ContentTypeDetector contentTypeDetector,
                                               AuditTrail auditTrail) {
        return new AttachmentService(attachmentRepository, issueRepository, projectAuthorization, fileStorage,
                contentTypeDetector, auditTrail);
    }

    // UTC: chart days are UTC days, whatever the server's own zone.
    @Bean
    public ChartService chartService(IssueRepository issueRepository,
                                     SprintRepository sprintRepository,
                                     IssueStatusRepository issueStatusRepository,
                                     AuditLogRepository auditLogRepository,
                                     ProjectAuthorization projectAuthorization) {
        return new ChartService(issueRepository, sprintRepository, issueStatusRepository, auditLogRepository,
                projectAuthorization, Clock.systemUTC());
    }

    @Bean
    public LabelService labelService(LabelRepository labelRepository,
                                     IssueRepository issueRepository,
                                     ProjectAuthorization projectAuthorization,
                                     AuditTrail auditTrail) {
        return new LabelService(labelRepository, issueRepository, projectAuthorization, auditTrail);
    }
}
