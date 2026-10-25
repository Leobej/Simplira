package io.github.leobej.support;

import io.github.leobej.domain.port.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// The raw invitation token exists only in the email, by design - so a test that accepts an
// invitation has to read it from the sender. Notification subjects are kept so the email channel
// can be checked separately from the in-app one.
@TestConfiguration
public class RecordingEmailConfig {

    @Bean
    @Primary
    public RecordingEmailService recordingEmailService() {
        return new RecordingEmailService();
    }

    public static class RecordingEmailService implements EmailService {

        private final Map<String, String> invitationTokens = new ConcurrentHashMap<>();
        private final Map<String, String> verificationTokens = new ConcurrentHashMap<>();
        private final Map<String, String> resetTokens = new ConcurrentHashMap<>();
        private final Map<String, String> changeTokens = new ConcurrentHashMap<>();
        private final Map<String, String> changedNotices = new ConcurrentHashMap<>();
        private final Map<String, List<String>> notificationSubjects = new ConcurrentHashMap<>();

        public String invitationTokenFor(String email) {
            return invitationTokens.get(email.toLowerCase(Locale.ROOT));
        }

        public List<String> notificationSubjectsFor(String email) {
            return notificationSubjects.getOrDefault(email.toLowerCase(Locale.ROOT), List.of());
        }

        @Override
        public void sendInvitation(String to, String workspaceName, String invitationToken) {
            invitationTokens.put(to.toLowerCase(Locale.ROOT), invitationToken);
        }

        @Override
        public void sendPasswordReset(String to, String resetToken) {
            resetTokens.put(to.toLowerCase(Locale.ROOT), resetToken);
        }

        public String resetTokenFor(String email) {
            return resetTokens.get(email.toLowerCase(Locale.ROOT));
        }

        @Override
        public void sendEmailChange(String to, String changeToken) {
            changeTokens.put(to.toLowerCase(Locale.ROOT), changeToken);
        }

        public String changeTokenFor(String email) {
            return changeTokens.get(email.toLowerCase(Locale.ROOT));
        }

        @Override
        public void sendEmailChangedNotice(String to, String newAddress) {
            changedNotices.put(to.toLowerCase(Locale.ROOT), newAddress);
        }

        public String changedNoticeFor(String email) {
            return changedNotices.get(email.toLowerCase(Locale.ROOT));
        }

        // The latest link sent to an address, the way someone would click the newest email.
        public String verificationTokenFor(String email) {
            return verificationTokens.get(email.toLowerCase(Locale.ROOT));
        }

        @Override
        public void sendEmailVerification(String to, String verificationToken) {
            verificationTokens.put(to.toLowerCase(Locale.ROOT), verificationToken);
        }

        @Override
        public void sendNotification(String to, String subject, String body) {
            notificationSubjects.computeIfAbsent(to.toLowerCase(Locale.ROOT), key -> new CopyOnWriteArrayList<>())
                    .add(subject);
        }
    }
}
