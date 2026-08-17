package io.github.leobej.application.api.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersNormalizedAndLogsInWhateverTheCasing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Casing@Simplira.com","password":"correct-horse","fullName":"Case Test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("casing@simplira.com"));

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"CASING@simplira.COM","password":"correct-horse"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(loginBody, "$.data.accessToken");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("casing@simplira.com"));
    }

    @Test
    void rejectsAnAlreadyRegisteredEmail() throws Exception {
        String body = """
                {"email":"duplicate@simplira.com","password":"correct-horse","fullName":"Dupe"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void rejectsUnauthenticatedAccessToCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
