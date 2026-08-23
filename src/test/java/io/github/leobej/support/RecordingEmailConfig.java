package io.github.leobej.support;

import io.github.leobej.domain.port.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The raw invitation token exists only in the email, by design - so a test that accepts an
// invitation has to read it from the sender.
@TestConfiguration
public class RecordingEmailConfig {

    @Bean
    @Primary
    public RecordingEmailService recordingEmailService() {
        return new RecordingEmailService();
    }

    public static class RecordingEmailService implements EmailService {

        private final Map<String, String> invitationTokens = new ConcurrentHashMap<>();

        public String invitationTokenFor(String email) {
            return invitationTokens.get(email.toLowerCase(Locale.ROOT));
        }

        @Override
        public void sendInvitation(String to, String workspaceName, String invitationToken) {
            invitationTokens.put(to.toLowerCase(Locale.ROOT), invitationToken);
        }

        @Override
        public void sendPasswordReset(String to, String resetToken) {
            // nothing to record yet
        }

        @Override
        public void sendNotification(String to, String subject, String body) {
            // nothing to record yet
        }
    }
}
