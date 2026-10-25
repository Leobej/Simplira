package io.github.leobej.application.api.user;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.application.api.auth.RefreshTokenCookie;
import io.github.leobej.support.PostgresTestBase;
import io.github.leobej.support.RecordingEmailConfig;
import io.github.leobej.support.RecordingEmailConfig.RecordingEmailService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(RecordingEmailConfig.class)
class AccountFlowTest extends PostgresTestBase {

    private static final String PASSWORD = "correct-horse";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailService emails;

    @Test
    void aResetLinkSetsANewPasswordAndEndsEverySession() throws Exception {
        String email = register();
        Cookie session = loginResult(email, PASSWORD).getResponse().getCookie(RefreshTokenCookie.NAME);

        forgot(email).andExpect(status().isOk());
        String token = emails.resetTokenFor(email);
        assertThat(token).isNotNull();

        reset(token, "battery-staple").andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        // The session from before the reset is over too: whoever had the old password is out.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        // The link came to this address, so following it proved the address.
        login(email, "battery-staple")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.emailVerified").value(true));
        // Single use.
        reset(token, "another-one")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void askingForAResetSaysTheSameWhetherOrNotTheAccountExists() throws Exception {
        String unknown = "nobody-" + shortId() + "@simplira.com";
        forgot(unknown)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(emails.resetTokenFor(unknown)).isNull();
    }

    @Test
    void aNewRequestReplacesTheEarlierLink() throws Exception {
        String email = register();
        forgot(email);
        String first = emails.resetTokenFor(email);
        forgot(email);
        String second = emails.resetTokenFor(email);

        assertThat(second).isNotEqualTo(first);
        reset(first, "battery-staple").andExpect(status().isBadRequest());
        reset(second, "battery-staple").andExpect(status().isNoContent());
    }

    @Test
    void aProfileNameCanBeChanged() throws Exception {
        String email = register();
        String token = accessToken(loginResult(email, PASSWORD));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"  Renamed Person  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Renamed Person"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.fullName").value("Renamed Person"));
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingThePasswordSignsOutEverywhereElseAndKeepsThisSessionGoing() throws Exception {
        String email = register();
        MvcResult phone = loginResult(email, PASSWORD);
        MvcResult laptop = loginResult(email, PASSWORD);
        String laptopToken = accessToken(laptop);

        changePassword(laptopToken, "not-my-password", "battery-staple")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURRENT_PASSWORD"));

        MvcResult changed = changePassword(laptopToken, PASSWORD, "battery-staple")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();
        Cookie fresh = changed.getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(fresh).isNotNull();

        // The other device is out; this one carries on with the cookie it was just given.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(phone.getResponse().getCookie(RefreshTokenCookie.NAME)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(fresh)).andExpect(status().isOk());
        login(email, "battery-staple").andExpect(status().isOk());
    }

    @Test
    void anOldTokenFromALoggedOutDeviceIsRefusedWithoutSigningOutTheOthers() throws Exception {
        String email = register();
        Cookie loggedOut = loginResult(email, PASSWORD).getResponse().getCookie(RefreshTokenCookie.NAME);
        Cookie stillIn = loginResult(email, PASSWORD).getResponse().getCookie(RefreshTokenCookie.NAME);
        mockMvc.perform(post("/api/v1/auth/logout").cookie(loggedOut)).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(loggedOut)).andExpect(status().isUnauthorized());
        // Not a replay of a rotated token, so not treated as theft.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(stillIn)).andExpect(status().isOk());
    }

    private String register() throws Exception {
        String email = "account-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Account\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private MvcResult loginResult(String email, String password) throws Exception {
        return login(email, password).andExpect(status().isOk()).andReturn();
    }

    private static String accessToken(MvcResult login) throws Exception {
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
