package io.github.leobej.application.api.attachment;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Against a real S3-compatible server, so the upload, the signed link and the delete are the
// SDK's actual requests rather than a mock's idea of them.
@AutoConfigureMockMvc
class AttachmentFlowTest extends PostgresTestBase {

    // Started once and never stopped, like the Postgres container, for the same reason.
    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:5.2.3")
            .withExposedPorts(9090)
            .waitingFor(Wait.forListeningPort());

    static {
        S3.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("simplira.storage.endpoint", () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(9090));
        registry.add("simplira.storage.region", () -> "us-east-1");
        registry.add("simplira.storage.access-key", () -> "test");
        registry.add("simplira.storage.secret-key", () -> "test");
        registry.add("simplira.storage.path-style", () -> "true");
        registry.add("simplira.storage.create-bucket-if-missing", () -> "true");
    }

    // A real 1x1 PNG.
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void anImageGoesUpAndComesBackThroughItsSignedLink() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team);

        // The client's claims are ignored: the name loses its path, the type comes from the bytes.
        String body = upload(team.dev(), issueId, "../../screens/shot.png", "text/plain", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("shot.png"))
                .andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.fileSize").value(PNG.length))
                .andExpect(jsonPath("$.data.urlExpiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.url");

        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(PNG);

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/attachments").header("Authorization", bearer(team.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].uploadedBy").value(team.dev().userId().toString()));
    }

    @Test
    void onlyImagesARealBrowserShowsAsImagesAreAccepted() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team);

        // HTML dressed as a PNG: the bytes say otherwise.
        upload(team.admin(), issueId, "evil.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
        // A genuine SVG is still refused: opened from a link, its script runs.
        upload(team.admin(), issueId, "logo.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        upload(team.admin(), issueId, "empty.png", "image/png", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMPTY_FILE"));
        upload(team.admin(), issueId, "huge.png", "image/png", new byte[10 * 1024 * 1024 + 1])
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void deletingTakesTheBytesWithItAndIsTheUploadersOrAnAdmins() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team);
        String body = upload(team.admin(), issueId, "secret.png", "image/png", PNG)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attachmentId = JsonPath.read(body, "$.data.id");
        String url = JsonPath.read(body, "$.data.url");

        mockMvc.perform(delete("/api/v1/attachments/" + attachmentId).header("Authorization", bearer(team.dev())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/attachments/" + attachmentId).header("Authorization", bearer(team.admin())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/attachments").header("Authorization", bearer(team.admin())))
                .andExpect(jsonPath("$.data.length()").value(0));
        // The link handed out before is dead too: the bytes are gone, not just hidden.
        HttpResponse<Void> afterwards = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(afterwards.statusCode()).isEqualTo(404);

        // And the issue's history kept the record.
        mockMvc.perform(get("/api/v1/issues/" + issueId + "/audit-log").header("Authorization", bearer(team.admin())))
                .andExpect(jsonPath("$.data[0].entityType").value("ATTACHMENT"))
                .andExpect(jsonPath("$.data[0].action").value("DELETED"))
                .andExpect(jsonPath("$.data[0].oldValue.fileName").value("secret.png"))
                .andExpect(jsonPath("$.data[1].action").value("CREATED"));
    }

    @Test
    void attachmentsAreAsVisibleAsTheirIssue() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team);
        Account outsider = signUp("Outsider");

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/attachments").header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_NOT_FOUND"));
        upload(outsider, issueId, "sneaky.png", "image/png", PNG)
                .andExpect(status().isNotFound());
    }

    private ResultActions upload(Account account, String issueId, String fileName, String contentType, byte[] bytes)
            throws Exception {
        return mockMvc.perform(multipart("/api/v1/issues/" + issueId + "/attachments")
                .file(new MockMultipartFile("file", fileName, contentType, bytes))
                .header("Authorization", bearer(account)));
    }

    private record Account(UUID userId, String token) {}

    private record Team(Account admin, Account dev, UUID projectId) {}

    private Team newTeam() throws Exception {
        Account admin = signUp("Admin");
        Account dev = signUp("Dev");
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Files " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, admin.userId()));
        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Files\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        return new Team(admin, dev, projectId);
    }

    private String createIssue(Team team) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"With pictures\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private Account signUp(String fullName) throws Exception {
        String email = "files-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\",\"fullName\":\""
                                + fullName + "\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(JsonPath.read(body, "$.data.user.id")),
                JsonPath.read(body, "$.data.accessToken"));
    }

    private static String bearer(Account account) {
        return "Bearer " + account.token();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
