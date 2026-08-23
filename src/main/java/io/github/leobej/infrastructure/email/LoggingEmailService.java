package io.github.leobej.infrastructure.email;

import io.github.leobej.domain.port.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Stub sender until a real provider is wired (§25). It writes the link to the log so local
// development can follow it.
//
// It logs the raw token on purpose, which is exactly what a real implementation must never do -
// replace this class before pointing the app at a live mailbox, do not add a provider alongside it.
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    private final String appBaseUrl;

    public LoggingEmailService(@Value("${simplira.app.base-url}") String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    public void sendInvitation(String to, String workspaceName, String invitationToken) {
        log.info("[stub email] invitation to={} workspace='{}' link={}/invitations/accept?token={}",
                to, workspaceName, appBaseUrl, invitationToken);
    }

    @Override
    public void sendPasswordReset(String to, String resetToken) {
        log.info("[stub email] password reset to={} link={}/reset-password?token={}",
                to, appBaseUrl, resetToken);
    }

    @Override
    public void sendNotification(String to, String subject, String body) {
        log.info("[stub email] notification to={} subject='{}'", to, subject);
    }
}
