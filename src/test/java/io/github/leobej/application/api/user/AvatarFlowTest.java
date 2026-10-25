package io.github.leobej.application.api.user;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.support.S3StorageTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AvatarFlowTest extends S3StorageTestBase {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAvatarIsAnUnauthenticatedPathThatRedirectsToTheImage() throws Exception {
        Account account = signUp();
        String avatarUrl = JsonPath.read(uploadAvatar(account, PNG)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.avatarUrl");
        assertThat(avatarUrl).startsWith("/api/v1/avatars/" + account.userId() + "/");

        // No Authorization header: this is what an <img> asks for.
        String signed = mockMvc.perform(get(avatarUrl))
                .andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "max-age=600, private"))
                .andReturn().getResponse().getHeader("Location");

        HttpResponse<byte[]> image = http.send(HttpRequest.newBuilder(URI.create(signed)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).isEqualTo(PNG);
    }

    @Test
    void replacingAnAvatarRetiresTheOldOneEverywhere() throws Exception {
        Account account = signUp();
        String first = JsonPath.read(uploadAvatar(account, PNG).andReturn().getResponse().getContentAsString(),
                "$.data.avatarUrl");
        String firstSigned = mockMvc.perform(get(first)).andReturn().getResponse().getHeader("Location");

        String second = JsonPath.read(uploadAvatar(account, PNG).andReturn().getResponse().getContentAsString(),
                "$.data.avatarUrl");
        assertThat(second).isNotEqualTo(first);

        // The old path is no longer this user's avatar, and its bytes are gone from storage.
        mockMvc.perform(get(first)).andExpect(status().isNotFound());
        assertThat(http.send(HttpRequest.newBuilder(URI.create(firstSigned)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        mockMvc.perform(get(second)).andExpect(status().isFound());
    }

    @Test
    void removingAnAvatarLeavesNone() throws Exception {
        Account account = signUp();
        String url = JsonPath.read(uploadAvatar(account, PNG).andReturn().getResponse().getContentAsString(),
                "$.data.avatarUrl");

        mockMvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + account.token()))
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void anAvatarHasToBeASmallImage() throws Exception {
        Account account = signUp();
        uploadAvatar(account, new byte[2 * 1024 * 1024 + 1])
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
        uploadAvatar(account, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        // A made-up avatar id is just not found, signed in or not.
        mockMvc.perform(get("/api/v1/avatars/" + account.userId() + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private ResultActions uploadAvatar(Account account, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/v1/users/me/avatar")
                .file(new MockMultipartFile("file", "me.png", "image/png", bytes))
                .header("Authorization", "Bearer " + account.token()));
    }

    private record Account(UUID userId, String token) {}

    private Account signUp() throws Exception {
        String email = "avatar-" + UUID.randomUUID().toString().substring(0, 8) + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\",\"fullName\":\"Avatar\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.data.user.id")),
                JsonPath.read(body, "$.data.accessToken"));
    }
}
