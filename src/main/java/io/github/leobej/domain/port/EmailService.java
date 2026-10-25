package io.github.leobej.domain.port;

// A port, not an infrastructure class: domain services send mail, and they may not import
// infrastructure. Implementations build the actual links and talk to a provider.
public interface EmailService {
    void sendInvitation(String to, String workspaceName, String invitationToken);
    void sendPasswordReset(String to, String resetToken);
    void sendEmailVerification(String to, String verificationToken);
    void sendNotification(String to, String subject, String body);
}
