package io.github.leobej.infrastructure.email;

import io.github.leobej.domain.port.EmailService;
import io.github.leobej.infrastructure.email.EmailTemplates.Email;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

// §25 over plain SMTP, which every provider speaks (Mailgun, SES, Postmark, Resend...): choosing
// one is configuration, not code. Locally, Mailpit catches everything (docker-compose).
//
// Two failure policies, on purpose:
// - Emails carrying a token are sent directly - an outbox would store the raw token - and a
//   failure is logged and swallowed, never the token. Whoever needed the link can ask again:
//   resend verification, forgot password again, or an admin re-sends the invitation (§25: "persist
//   it, log the failure, and let resend be the recovery path").
// - Notification emails throw, because they come from the outbox, whose retries depend on it.
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String appBaseUrl;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${simplira.mail.from}") String from,
                            @Value("${simplira.app.base-url}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    public void sendInvitation(String to, String workspaceName, String invitationToken) {
        sendWithToken("invitation", to, EmailTemplates.invitation(workspaceName, link("/invitations/accept", invitationToken)));
    }

    @Override
    public void sendPasswordReset(String to, String resetToken) {
        sendWithToken("password reset", to, EmailTemplates.passwordReset(link("/reset-password", resetToken)));
    }

    @Override
    public void sendEmailVerification(String to, String verificationToken) {
        sendWithToken("email verification", to, EmailTemplates.emailVerification(link("/verify-email", verificationToken)));
    }

    @Override
    public void sendEmailChange(String to, String changeToken) {
        sendWithToken("email change", to, EmailTemplates.emailChange(link("/confirm-email-change", changeToken)));
    }

    // No token, but the same policy: the change has already happened, so a failed notice is logged.
    @Override
    public void sendEmailChangedNotice(String to, String newAddress) {
        try {
            send(to, EmailTemplates.emailChanged(newAddress));
        } catch (MailException | MessagingException ex) {
            log.error("Email delivery failed: type=email changed notice, to={}", to, ex);
        }
    }

    @Override
    public void sendNotification(String to, String subject, String body) {
        try {
            send(to, EmailTemplates.notification(subject, body, appBaseUrl));
        } catch (MessagingException ex) {
            throw new IllegalStateException("Could not build the notification email", ex);
        }
    }

    private void sendWithToken(String type, String to, Email email) {
        try {
            send(to, email);
        } catch (MailException | MessagingException ex) {
            // The message is deliberately not logged: it contains the link.
            log.error("Email delivery failed: type={}, to={}", type, to, ex);
        }
    }

    private void send(String to, Email email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(email.subject());
        helper.setText(email.text(), email.html());
        mailSender.send(message);
    }

    // The frontend's page for the link, not this API: the page reads the token and posts it back.
    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(appBaseUrl).path(path).queryParam("token", token)
                .build().encode().toUriString();
    }
}
