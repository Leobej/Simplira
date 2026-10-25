package io.github.leobej.application.api.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.support.PostgresTestBase;
import io.github.leobej.support.RecordingEmailConfig;
import io.github.leobej.support.RecordingEmailConfig.RecordingEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The one suite that runs with the gate on, the way the app does: the test profile switches it off
// so suites about other things need not verify every account they create.
@AutoConfigureMockMvc
@Import(RecordingEmailConfig.class)
@TestPropertySource(properties = "simplira.auth.require-verified-email=true")
class EmailVerificationFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailService emails;

    @Test
    void registeringSendsALinkThatVerifiesOnce() throws Exception {
        String email = newEmail();
        register(email).andExpect(jsonPath("$.data.emailVerified").value(false));
        String token = emails.verificationTokenFor(email);
        assertThat(token).isNotNull();

        verify(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(true));
        // Single use.
        verify(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void anUnverifiedAccountCanLogInButNotCreateAWorkspace() throws Exception {
        String email = newEmail();
        register(email);
        String token = login(email);

        createWorkspace(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));

        verify(emails.verificationTokenFor(email)).andExpect(status().isOk());
        createWorkspace(token).andExpect(status().isCreated());
    }

    @Test
    void resendingReplacesTheOldLinkAndSaysNothingAboutWhetherTheAccountExists() throws Exception {
        String email = newEmail();
        register(email);
        String first = emails.verificationTokenFor(email);

        resend(email).andExpect(status().isOk());
        String second = emails.verificationTokenFor(email);
        assertThat(second).isNotEqualTo(first);

        verify(first).andExpect(status().isBadRequest());
        verify(second).andExpect(status().isOk());

        // Unknown and already-verified addresses get exactly the same answer, and no email.
        resend(newEmail()).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        resend(email).andExpect(status().isOk());
        assertThat(emails.verificationTokenFor(email)).isEqualTo(second);
    }

    @Test
    void anInvitationToYourOwnAddressIsItselfTheProof() throws Exception {
        String adminEmail = newEmail();
        register(adminEmail);
        verify(emails.verificationTokenFor(adminEmail));
        String admin = login(adminEmail);
        String workspaceId = JsonPath.read(createWorkspace(admin).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        String inviteeEmail = newEmail();
        invite(admin, workspaceId, inviteeEmail);
        String invitationToken = emails.invitationTokenFor(inviteeEmail);

        // Someone else, unverified, holding a forwarded link: no proof of anything.
        String bystanderEmail = newEmail();
        register(bystanderEmail);
        accept(login(bystanderEmail), invitationToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));

        // The invitee, unverified, accepting their own invitation: that is the proof. And the
        // refusal above did not use the invitation up.
        register(inviteeEmail);
        accept(login(inviteeEmail), invitationToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\",\"fullName\":\"Verifier\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions createWorkspace(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/workspaces")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Verified " + UUID.randomUUID().toString().substring(0, 8) + "\"}"));
    }

    private void invite(String token, String workspaceId, String email) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions accept(String token, String invitationToken) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/accept")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + invitationToken + "\"}"));
    }

    private static String newEmail() {
        return "verify-" + UUID.randomUUID().toString().substring(0, 8) + "@simplira.com";
    }
}
