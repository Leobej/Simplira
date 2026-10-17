package io.github.leobej.application.api.notification;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationEntityType;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.NotificationRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.domain.service.EmailOutboxDispatcher;
import io.github.leobej.domain.service.NotificationService;
import io.github.leobej.support.PostgresTestBase;
import io.github.leobej.support.RecordingEmailConfig;
import io.github.leobej.support.RecordingEmailConfig.RecordingEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(RecordingEmailConfig.class)
class NotificationFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RecordingEmailService emails;

    @Autowired
    private EmailOutboxDispatcher emailOutboxDispatcher;

    @Test
    void assigningSomeoneTellsThemOnceAndNeverTheAssigner() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team, "Wire the bell");

        assign(team.admin(), issueId, team.dev().userId());
        assign(team.admin(), issueId, team.dev().userId());   // a repeat is not news
        assign(team.admin(), issueId, team.admin().userId()); // nor is your own action

        inbox(team.dev())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].action").value("ASSIGNED"))
                .andExpect(jsonPath("$.data[0].title").value("Admin assigned you to NTF-1"))
                .andExpect(jsonPath("$.data[0].body").value("Wire the bell"))
                .andExpect(jsonPath("$.data[0].entityType").value("ISSUE"))
                .andExpect(jsonPath("$.data[0].entityId").value(issueId))
                .andExpect(jsonPath("$.data[0].read").value(false));
        inbox(team.admin()).andExpect(jsonPath("$.meta.total").value(0));

        // Both channels are on until someone says otherwise - and email waits for the outbox.
        assertThat(emails.notificationSubjectsFor(team.dev().email())).isEmpty();
        drainOutbox();
        assertThat(emails.notificationSubjectsFor(team.dev().email())).containsExactly("Admin assigned you to NTF-1");
    }

    @Test
    void aMentionIsTheOnlyNotificationItsTargetGetsForThatComment() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team, "Review me");   // reported by Admin
        assign(team.admin(), issueId, team.dev().userId());

        comment(team.dev(), issueId, "Over to @[Admin](" + team.admin().userId() + ") for review");
        inbox(team.admin())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].action").value("MENTIONED"))
                .andExpect(jsonPath("$.data[0].title").value("Dev mentioned you on NTF-1"))
                // Rendered for reading, not left as the editor's token.
                .andExpect(jsonPath("$.data[0].body").value("Over to @Admin for review"));

        comment(team.dev(), issueId, "Pushed a fix");
        inbox(team.admin())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data[0].action").value("COMMENT_ADDED"))
                .andExpect(jsonPath("$.data[0].title").value("Dev commented on NTF-1"));

        // The commenter is told nothing about their own comments.
        inbox(team.dev()).andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void reachingDoneIsToldAsClosedRatherThanAsAStatusChange() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team, "Ship it");
        assign(team.admin(), issueId, team.dev().userId());
        Map<String, String> inProgress = statusIn(team, "IN_PROGRESS");
        Map<String, String> done = statusIn(team, "DONE");

        moveTo(team.admin(), issueId, inProgress.get("id"));
        moveTo(team.admin(), issueId, done.get("id"));

        inbox(team.dev())
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.data[0].action").value("ISSUE_CLOSED"))
                .andExpect(jsonPath("$.data[0].title").value("Admin closed NTF-1"))
                .andExpect(jsonPath("$.data[1].action").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.data[1].title").value("Admin moved NTF-1 to " + inProgress.get("name")));
    }

    @Test
    void aLinkThatBlocksIsToldAsTheBlock() throws Exception {
        Team team = newTeam();
        String blocker = createIssue(team, "Provision staging");
        String blocked = createIssue(team, "Deploy to staging");
        String related = createIssue(team, "Write the runbook");
        assign(team.admin(), blocked, team.dev().userId());

        link(team.admin(), blocker, blocked, "BLOCKS");
        link(team.admin(), related, blocked, "RELATED_TO");

        inbox(team.dev())
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.data[0].action").value("LINKED"))
                .andExpect(jsonPath("$.data[0].body").value("NTF-3 relates to NTF-2: Write the runbook"))
                .andExpect(jsonPath("$.data[1].action").value("ISSUE_BLOCKED"))
                .andExpect(jsonPath("$.data[1].title").value("Admin blocked NTF-2"))
                .andExpect(jsonPath("$.data[1].body").value("Blocked by NTF-1: Provision staging"));
    }

    @Test
    void aPreferenceTurnsOffOneChannelForOneActionInOneWorkspace() throws Exception {
        Team team = newTeam();
        mockMvc.perform(patch("/api/v1/workspaces/" + team.workspaceId() + "/notification-preferences")
                        .header("Authorization", bearer(team.dev()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":[{\"action\":\"ASSIGNED\",\"inApp\":false}]}"))
                .andExpect(status().isOk());

        String preferences = mockMvc.perform(get("/api/v1/workspaces/" + team.workspaceId() + "/notification-preferences")
                        .header("Authorization", bearer(team.dev())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(NotificationAction.values().length))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(preferences, "$.data[?(@.action == 'ASSIGNED')].inApp")).containsExactly(false);
        // The channel not mentioned kept its default.
        assertThat(JsonPath.<List<Boolean>>read(preferences, "$.data[?(@.action == 'ASSIGNED')].email")).containsExactly(true);

        assign(team.admin(), createIssue(team, "Quietly"), team.dev().userId());
        inbox(team.dev()).andExpect(jsonPath("$.meta.total").value(0));
        drainOutbox();
        assertThat(emails.notificationSubjectsFor(team.dev().email())).containsExactly("Admin assigned you to NTF-1");

        // Someone outside the workspace is told it does not exist.
        Account outsider = signUp("Outsider");
        mockMvc.perform(get("/api/v1/workspaces/" + team.workspaceId() + "/notification-preferences")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void readingAndDismissingTouchOnlyYourOwn() throws Exception {
        Team team = newTeam();
        assign(team.admin(), createIssue(team, "One"), team.dev().userId());
        assign(team.admin(), createIssue(team, "Two"), team.dev().userId());
        String newest = JsonPath.read(inbox(team.dev()).andReturn().getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(patch("/api/v1/notifications/" + newest + "/read").header("Authorization", bearer(team.admin())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/notifications/" + newest + "/read").header("Authorization", bearer(team.dev())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").exists());
        unread(team.dev()).andExpect(jsonPath("$.meta.total").value(1));

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", bearer(team.dev())))
                .andExpect(status().isNoContent());
        unread(team.dev()).andExpect(jsonPath("$.meta.total").value(0));

        mockMvc.perform(delete("/api/v1/notifications/" + newest).header("Authorization", bearer(team.dev())))
                .andExpect(status().isNoContent());
        inbox(team.dev()).andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void leavingAWorkspaceTakesItsNotificationsOutOfTheBell() throws Exception {
        Team team = newTeam();
        assign(team.admin(), createIssue(team, "Left behind"), team.dev().userId());
        inbox(team.dev()).andExpect(jsonPath("$.meta.total").value(1));

        mockMvc.perform(delete("/api/v1/workspaces/" + team.workspaceId() + "/members/me")
                        .header("Authorization", bearer(team.dev())))
                .andExpect(status().isNoContent());

        inbox(team.dev()).andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void startingASprintTellsWhoeverHasWorkInIt() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team, "Sprint work");
        assign(team.admin(), issueId, team.dev().userId());
        String sprintId = JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/sprints")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 1\",\"startDate\":\"2026-10-19\",\"endDate\":\"2026-11-02\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/move-to-sprint")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintId\":\"" + sprintId + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/start").header("Authorization", bearer(team.admin())))
                .andExpect(status().isOk());

        inbox(team.dev())
                .andExpect(jsonPath("$.data[0].action").value("SPRINT_STARTED"))
                .andExpect(jsonPath("$.data[0].title").value("Admin started Sprint 1"))
                .andExpect(jsonPath("$.data[0].entityType").value("SPRINT"))
                .andExpect(jsonPath("$.data[0].entityId").value(sprintId));
    }

    @Test
    void expiredNotificationsArePurged() throws Exception {
        Team team = newTeam();
        Instant longAgo = Instant.now().minus(Duration.ofDays(91));
        Notification expired = notificationRepository.save(new Notification(UUID.randomUUID(), team.workspaceId(),
                team.dev().userId(), team.admin().userId(), NotificationEntityType.ISSUE, UUID.randomUUID(),
                NotificationAction.ASSIGNED, "Old news", null, false, null, longAgo, longAgo.plus(Duration.ofDays(90))));
        Notification current = notificationRepository.save(Notification.create(team.workspaceId(),
                team.dev().userId(), team.admin().userId(), NotificationEntityType.ISSUE, UUID.randomUUID(),
                NotificationAction.ASSIGNED, "Fresh", null, Duration.ofDays(90)));

        assertThat(notificationService.purgeExpired()).isGreaterThanOrEqualTo(1);

        assertThat(notificationRepository.findById(expired.getId())).isEmpty();
        assertThat(notificationRepository.findById(current.getId())).isPresent();
    }

    // Email is queued, not sent, and the poller is off in tests. The outbox is shared with every
    // other test class, so keep going until a pass sends nothing.
    private void drainOutbox() {
        while (emailOutboxDispatcher.dispatchDue() > 0) {
            // next batch
        }
    }

    private record Account(UUID userId, String token, String email) {}

    private record Team(Account admin, Account dev, UUID workspaceId, UUID projectId) {}

    private ResultActions inbox(Account account) throws Exception {
        return mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer(account)))
                .andExpect(status().isOk());
    }

    private ResultActions unread(Account account) throws Exception {
        return mockMvc.perform(get("/api/v1/notifications?unread=true").header("Authorization", bearer(account)))
                .andExpect(status().isOk());
    }

    private void assign(Account actor, String issueId, UUID userId) throws Exception {
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isOk());
    }

    private void comment(Account author, String issueId, String content) throws Exception {
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/comments")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());
    }

    private void link(Account actor, String source, String target, String type) throws Exception {
        mockMvc.perform(post("/api/v1/issues/" + source + "/links")
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIssueId\":\"" + target + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated());
    }

    private void moveTo(Account actor, String issueId, String statusId) throws Exception {
        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusId\":\"" + statusId + "\"}"))
                .andExpect(status().isOk());
    }

    private Map<String, String> statusIn(Team team, String category) throws Exception {
        String body = mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/statuses")
                        .header("Authorization", bearer(team.admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> matches = JsonPath.read(body, "$.data[?(@.category == '" + category + "')]");
        return Map.of("id", (String) matches.get(0).get("id"), "name", (String) matches.get(0).get("name"));
    }

    private String createIssue(Team team, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private Team newTeam() throws Exception {
        Account admin = signUp("Admin");
        Account dev = signUp("Dev");
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Notify " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, admin.userId()));

        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Notified\",\"key\":\"NTF\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        return new Team(admin, dev, workspaceId, projectId);
    }

    private Account signUp(String fullName) throws Exception {
        String email = "notify-" + shortId() + "@simplira.com";
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
                JsonPath.read(body, "$.data.accessToken"), email);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.token();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
