package io.github.leobej.infrastructure.messaging;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A real server and a real STOMP client: the handshake, the CONNECT check and the user-destination
// routing are exactly the parts a MockMvc test would skip.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class NotificationPushTest extends PostgresTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Autowired
    private SimpUserRegistry userRegistry;

    private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

    {
        // Read every frame as text, whatever its content-type: the push is application/json, which
        // the plain StringMessageConverter would refuse.
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
    void aNotificationIsPushedToItsRecipientTheMomentItIsCreated() throws Exception {
        Account admin = signUp("Admin");
        Account dev = signUp("Dev");
        UUID projectId = projectWith(admin, dev);

        Recorder devInbox = new Recorder();
        StompSession session = connect(dev, devInbox);
        session.subscribe("/user/queue/notifications", devInbox);
        awaitSubscribed(dev);

        String issueId = createIssue(admin, projectId);
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + dev.userId() + "\"}"))
                .andExpect(status().isOk());

        String pushed = devInbox.frames.poll(5, TimeUnit.SECONDS);
        assertThat(pushed).as("pushed frame; client errors: %s", devInbox.errors).isNotNull();
        assertThat(JsonPath.<String>read(pushed, "$.type")).isEqualTo("NOTIFICATION_CREATED");
        assertThat(JsonPath.<String>read(pushed, "$.action")).isEqualTo("ASSIGNED");
        assertThat(JsonPath.<String>read(pushed, "$.entityId")).isEqualTo(issueId);
    }

    @Test
    void aConnectWithoutAValidTokenIsRefused() throws Exception {
        Recorder recorder = new Recorder();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer not-a-real-token");

        stompClient.connectAsync(url(), new WebSocketHttpHeaders(), headers, recorder);

        assertThat(recorder.errors.poll(5, TimeUnit.SECONDS)).contains("Invalid or expired access token");
    }

    @Test
    void onlyTheNotificationQueueCanBeSubscribedTo() throws Exception {
        Account someone = signUp("Someone");
        Recorder recorder = new Recorder();
        StompSession session = connect(someone, recorder);

        // A raw queue name is how one session could listen to another's user queue.
        session.subscribe("/queue/notifications-user" + UUID.randomUUID(), recorder);

        assertThat(recorder.errors.poll(5, TimeUnit.SECONDS)).contains("Cannot subscribe");
    }

    private StompSession connect(Account account, Recorder recorder) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + account.token());
        return stompClient.connectAsync(url(), new WebSocketHttpHeaders(), headers, recorder)
                .get(5, TimeUnit.SECONDS);
    }

    // SUBSCRIBE has no reply, so wait until the server has registered it before triggering a push.
    private void awaitSubscribed(Account account) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var user = userRegistry.getUser(account.userId().toString());
            if (user != null && user.getSessions().stream().anyMatch(s -> !s.getSubscriptions().isEmpty())) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("The subscription never registered");
    }

    private String url() {
        return "ws://localhost:" + port + "/ws";
    }

    // Collects pushed frames, and ERROR frames or transport failures as errors.
    private static class Recorder extends StompSessionHandlerAdapter {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

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

    private UUID projectWith(Account admin, Account dev) throws Exception {
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + admin.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Push " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, admin.userId()));
        return UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", "Bearer " + admin.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Pushed\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
    }

    private String createIssue(Account actor, UUID projectId) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + actor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Pushed issue\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private Account signUp(String fullName) throws Exception {
        String email = "push-" + shortId() + "@simplira.com";
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

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
