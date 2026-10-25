package io.github.leobej.infrastructure.email;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The real sender against a real SMTP server: Mailpit, the same one docker-compose runs, read back
// through its HTTP API. No Spring context - this is about what goes over the wire.
class SmtpEmailServiceTest {

    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.31.4")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

    static {
        MAILPIT.start();
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private SmtpEmailService email;

    @BeforeEach
    void sender() {
        email = new SmtpEmailService(mailSender(MAILPIT.getMappedPort(1025)), "Simplira <no-reply@simplira.test>",
                "https://app.simplira.test");
    }

    @Test
    void aVerificationEmailCarriesTheLinkInBothTextAndHtml() throws Exception {
        String to = newAddress();
        email.sendEmailVerification(to, "tok-123_ABC");

        Map<String, Object> message = messageTo(to);
        assertThat(message.get("Subject")).isEqualTo("Confirm your email address for Simplira");
        assertThat((String) message.get("Text")).contains("https://app.simplira.test/verify-email?token=tok-123_ABC");
        assertThat((String) message.get("HTML")).contains("href=\"https://app.simplira.test/verify-email?token=tok-123_ABC\"");
        assertThat(JsonPath.<String>read(message, "$.From.Address")).isEqualTo("no-reply@simplira.test");
    }

    @Test
    void whatAUserTypedIsEscapedInTheHtmlAndKeptToOneLineInTheSubject() throws Exception {
        String to = newAddress();
        email.sendInvitation(to, "<script>alert(1)</script>\nBcc: victim@example.com", "tok");

        Map<String, Object> message = messageTo(to);
        assertThat((String) message.get("HTML")).doesNotContain("<script>").contains("&lt;script&gt;");
        // The line break would otherwise start a header of its own.
        assertThat((String) message.get("Subject")).doesNotContain("\n");
        assertThat(JsonPath.<List<Object>>read(message, "$.Bcc")).isEmpty();
    }

    @Test
    void aNotificationEmailUsesItsTitleAsTheSubject() throws Exception {
        String to = newAddress();
        email.sendNotification(to, "Admin assigned you to SB-12", "Fix the login button");

        Map<String, Object> message = messageTo(to);
        assertThat(message.get("Subject")).isEqualTo("Admin assigned you to SB-12");
        assertThat((String) message.get("Text")).contains("Fix the login button").contains("https://app.simplira.test");
    }

    @Test
    void anUnreachableServerFailsANotificationButNotATokenEmail() {
        // Nothing listens on this port. The outbox needs notification failures to retry them;
        // a token email is logged and dropped, and its recipient asks again.
        SmtpEmailService unreachable = new SmtpEmailService(mailSender(1), "no-reply@simplira.test",
                "https://app.simplira.test");

        assertThatThrownBy(() -> unreachable.sendNotification("someone@simplira.test", "Title", "Body"))
                .isInstanceOf(RuntimeException.class);
        unreachable.sendPasswordReset("someone@simplira.test", "tok");
    }

    private static JavaMailSenderImpl mailSender(int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(MAILPIT.getHost());
        sender.setPort(port);
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "2000");
        return sender;
    }

    private Map<String, Object> messageTo(String address) throws Exception {
        String list = get("/api/v1/search?query=" + java.net.URLEncoder.encode("to:" + address,
                java.nio.charset.StandardCharsets.UTF_8));
        List<String> ids = JsonPath.read(list, "$.messages[*].ID");
        assertThat(ids).as("messages to %s", address).hasSize(1);
        return JsonPath.read(get("/api/v1/message/" + ids.get(0)), "$");
    }

    private String get(String path) throws Exception {
        URI uri = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path);
        return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String newAddress() {
        return "smtp-" + UUID.randomUUID().toString().substring(0, 8) + "@simplira.test";
    }
}
