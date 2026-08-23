package io.github.leobej.application.api.auth;

import io.github.leobej.support.PostgresTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RefreshFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIssuesAnHttpOnlyRefreshCookie() throws Exception {
        Cookie cookie = signIn("cookie-shape@simplira.com");

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.getMaxAge()).isEqualTo(2592000);   // 30 days, per the design doc
    }

    @Test
    void theRawTokenIsNeverReturnedInTheBody() throws Exception {
        String body = signInResponse("body-check@simplira.com").getResponse().getContentAsString();

        assertThat(body).contains("accessToken");
        assertThat(body).doesNotContain("refreshToken");
    }

    @Test
    void refreshRotatesTheCookieAndIssuesANewAccessToken() throws Exception {
        Cookie first = signIn("rotation@simplira.com");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie second = refreshed.getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void replayingARotatedTokenRevokesEverySession() throws Exception {
        Cookie stolen = signIn("theft@simplira.com");
        Cookie current = refresh(stolen);          // stolen is rotated out and revoked

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(stolen))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        // The replay is treated as theft, so the honest client's live token dies with it.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(current))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Cookie cookie = signIn("logout@simplira.com");

        MvcResult loggedOut = mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(loggedOut.getResponse().getCookie(RefreshTokenCookie.NAME).getMaxAge()).isZero();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutACookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    private Cookie signIn(String email) throws Exception {
        Cookie cookie = signInResponse(email).getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private MvcResult signInResponse(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"correct-horse","fullName":"Session Test"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"correct-horse"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Cookie refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(RefreshTokenCookie.NAME);
    }
}
