package io.github.leobej.application.api.user;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.support.PostgresTestBase;
import io.github.leobej.support.RecordingEmailConfig.RecordingEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EmailChangeFlowTest extends PostgresTestBase {

    private static final String PASSWORD = "correct-horse";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailService emails;

    @Test
    void theAddressChangesOnlyWhenTheLinkSentToItIsFollowed() throws Exception {
        String oldEmail = register();
        String newEmail = newAddress();
        String token = accessToken(oldEmail);

        requestChange(token, newEmail, PASSWORD).andExpect(status().isAccepted());
        // Nothing has changed yet.
        login(oldEmail).andExpect(status().isOk());
        login(newEmail).andExpect(status().isUnauthorized());

        String link = emails.changeTokenFor(newEmail);
        assertThat(link).isNotNull();
        confirm(link)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail))
                // Following a link sent to it proved the new address.
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        login(newEmail).andExpect(status().isOk());
        login(oldEmail).andExpect(status().isUnauthorized());
        // The old address hears about it, in case it was not its owner who asked.
        assertThat(emails.changedNoticeFor(oldEmail)).isEqualTo(newEmail);
        // Single use.
        confirm(link)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EMAIL_CHANGE_TOKEN"));
    }

    @Test
    void askingNeedsThePasswordAndAnAddressThatIsNewAndFree() throws Exception {
        String email = register();
        String taken = register();
        String token = accessToken(email);

        requestChange(token, newAddress(), "not-my-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURRENT_PASSWORD"));
        requestChange(token, email.toUpperCase(), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_UNCHANGED"));
        requestChange(token, taken, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void anAddressTakenBeforeTheLinkIsFollowedIsRefusedAtConfirmation() throws Exception {
        String email = register();
        String wanted = newAddress();
        requestChange(accessToken(email), wanted, PASSWORD).andExpect(status().isAccepted());
        String link = emails.changeTokenFor(wanted);

        // Someone registers the address in the meantime.
        registerAs(wanted);

        confirm(link)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
        login(email).andExpect(status().isOk());
    }

    @Test
    void aNewRequestReplacesThePendingOne() throws Exception {
        String email = register();
        String token = accessToken(email);
        String first = newAddress();
        String second = newAddress();

        requestChange(token, first, PASSWORD);
        requestChange(token, second, PASSWORD);

        confirm(emails.changeTokenFor(first)).andExpect(status().isBadRequest());
        confirm(emails.changeTokenFor(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(second));
    }

    private ResultActions requestChange(String accessToken, String newEmail, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/email")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"" + password + "\"}"));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/confirm-email-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private String accessToken(String email) throws Exception {
        return JsonPath.read(login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");
    }

    private String register() throws Exception {
        String email = newAddress();
        registerAs(email);
        return email;
    }

    private void registerAs(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Mover\"}"))
                .andExpect(status().isCreated());
    }

    private static String newAddress() {
        return "move-" + UUID.randomUUID().toString().substring(0, 8) + "@simplira.com";
    }
}
