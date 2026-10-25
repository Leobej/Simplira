package io.github.leobej.infrastructure.messaging;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.infrastructure.messaging.config.ConnectionSweeper;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// §8's board, issue and workspace topics against a real server and a real STOMP client.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LiveUpdatesTest extends PostgresTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Autowired
    private SimpUserRegistry userRegistry;

    @Autowired
    private ConnectionSweeper connectionSweeper;

    private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

    {
        // Read every frame as text, whatever its content-type.
        stompClient.setMessageConverter(new StringMessageConverter() {
            @Override
            protected boolean supportsMimeType(MessageHeaders headers) {
                return true;
            }
        });
    }

    @AfterEach
    void stopClient() {
        stompClient.stop();
    }

    @Test
    void aBoardSeesIssuesArriveAndMoveWithoutReloading() throws Exception {
        Team team = newTeam("PUBLIC");
        Recorder board = new Recorder();
        connect(team.dev(), board).subscribe("/topic/project/" + team.projectId(), board);
        awaitSubscribed(team.dev());

        String issueId = createIssue(team);
        String created = board.next();
        assertThat(JsonPath.<String>read(created, "$.type")).isEqualTo("ISSUE_CREATED");
        assertThat(JsonPath.<String>read(created, "$.issueId")).isEqualTo(issueId);
        assertThat(JsonPath.<String>read(created, "$.actorId")).isEqualTo(team.admin().userId().toString());
        // Lean: what changed, never the content.
        assertThat(created).doesNotContain("Live issue");

        // Reordering is not in the audit log, but a shared board needs it most of all.
        mockMvc.perform(patch("/api/v1/issues/" + issueId + "/position")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        assertThat(JsonPath.<String>read(board.next(), "$.type")).isEqualTo("ISSUE_REPOSITIONED");
    }

    @Test
    void anIssuePageSeesTheConversationOnIt() throws Exception {
        Team team = newTeam("PUBLIC");
        String issueId = createIssue(team);
        Recorder page = new Recorder();
        connect(team.dev(), page).subscribe("/topic/issue/" + issueId, page);
        awaitSubscribed(team.dev());

        mockMvc.perform(post("/api/v1/issues/" + issueId + "/comments")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Looking now\"}"))
                .andExpect(status().isCreated());

        String event = page.next();
        assertThat(JsonPath.<String>read(event, "$.type")).isEqualTo("COMMENT_CREATED");
        assertThat(JsonPath.<String>read(event, "$.issueId")).isEqualTo(issueId);
    }

    @Test
    void onlySomeoneWhoCouldReadItOverRestMayWatchIt() throws Exception {
        // dev is in the workspace but not on this PRIVATE project.
        Team team = newTeam("PRIVATE");
        String issueId = createIssue(team);
        Account outsider = signUp("Outsider");

        assertRefused(team.dev(), "/topic/project/" + team.projectId());
        assertRefused(team.dev(), "/topic/issue/" + issueId);
        assertRefused(outsider, "/topic/workspace/" + team.workspaceId());
        assertRefused(outsider, "/topic/project/not-a-uuid");
        assertRefused(outsider, "/topic/anything-else");
    }

    @Test
    void aConnectionEndsOnceTheTokenItOpenedWithExpires() throws Exception {
        Team team = newTeam("PUBLIC");
        Recorder board = new Recorder();
        connect(team.dev(), board).subscribe("/topic/project/" + team.projectId(), board);
        awaitSubscribed(team.dev());

        // While the token is good, a sweep leaves the connection alone.
        connectionSweeper.sweep(Instant.now());
        createIssue(team);
        assertThat(JsonPath.<String>read(board.next(), "$.type")).isEqualTo("ISSUE_CREATED");
        assertThat(board.errors).isEmpty();

        // Tokens last 15 minutes.
        connectionSweeper.sweep(Instant.now().plus(Duration.ofMinutes(16)));
        assertThat(board.errors.poll(5, TimeUnit.SECONDS)).isEqualTo("Access token expired");
        awaitDisconnected(team.dev());
    }

    @Test
    void aConnectionEndsWhenWhatItWatchesIsNoLongerItsToSee() throws Exception {
        Team team = newTeam("PUBLIC");
        Recorder board = new Recorder();
        connect(team.dev(), board).subscribe("/topic/project/" + team.projectId(), board);
        awaitSubscribed(team.dev());

        // dev is in the workspace but not on the project, so making it PRIVATE shuts them out.
        mockMvc.perform(patch("/api/v1/projects/" + team.projectId())
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());
        connectionSweeper.sweep(Instant.now());

        assertThat(board.errors.poll(5, TimeUnit.SECONDS))
                .isEqualTo("No longer allowed to subscribe to /topic/project/" + team.projectId());
        awaitDisconnected(team.dev());
    }

    private void assertRefused(Account account, String destination) throws Exception {
        Recorder recorder = new Recorder();
        connect(account, recorder).subscribe(destination, recorder);
        assertThat(recorder.errors.poll(5, TimeUnit.SECONDS)).as(destination).contains("Cannot subscribe");
    }

    private StompSession connect(Account account, Recorder recorder) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + account.token());
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers, recorder)
                .get(5, TimeUnit.SECONDS);
    }

    // SUBSCRIBE has no reply, so wait until the server has registered it before changing anything.
    private void awaitSubscribed(Account account) throws InterruptedException {
        await("The subscription never registered", () -> {
            var user = userRegistry.getUser(account.userId().toString());
            return user != null && user.getSessions().stream().anyMatch(s -> !s.getSubscriptions().isEmpty());
        });
    }

    // The ERROR frame is not the end of it: the socket has to actually close.
    private void awaitDisconnected(Account account) throws InterruptedException {
        await("The session was never closed", () -> userRegistry.getUser(account.userId().toString()) == null);
    }

    private static void await(String failure, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(failure);
    }

    private static class Recorder extends StompSessionHandlerAdapter {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

        String next() throws InterruptedException {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("pushed frame; client errors: %s", errors).isNotNull();
            return frame;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (headers.containsKey("message")) {
                errors.add(headers.getFirst("message"));
            } else {
                frames.add((String) payload);
            }
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                    byte[] payload, Throwable exception) {
            errors.add(String.valueOf(exception.getMessage()));
        }
    }

    private record Account(UUID userId, String token) {}

    private record Team(Account admin, Account dev, UUID workspaceId, UUID projectId) {}

    private Team newTeam(String visibility) throws Exception {
        Account admin = signUp("Admin");
        Account dev = signUp("Dev");
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Live " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, admin.userId()));
        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Live\",\"visibility\":\"" + visibility + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        return new Team(admin, dev, workspaceId, projectId);
    }

    private String createIssue(Team team) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Live issue\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private Account signUp(String fullName) throws Exception {
        String email = "live-" + shortId() + "@simplira.com";
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
