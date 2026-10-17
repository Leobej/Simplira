package io.github.leobej.application.api.audit;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditLogFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anIssuesHistoryTellsItsStoryNewestFirst() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team, "Draft");

        edit(team.admin(), issueId, "{\"title\":\"Final\",\"priority\":\"HIGH\"}");
        edit(team.admin(), issueId, "{\"title\":\"Final\"}");          // changes nothing, so records nothing
        edit(team.admin(), issueId, "{\"statusId\":\"" + statusIn(team, "IN_PROGRESS") + "\"}");
        assign(team.admin(), issueId, team.dev().userId());
        comment(team.dev(), issueId, "On it");

        history(team.dev(), issueId)
                .andExpect(jsonPath("$.meta.total").value(5))
                .andExpect(jsonPath("$.data[0].entityType").value("COMMENT"))
                .andExpect(jsonPath("$.data[0].action").value("CREATED"))
                .andExpect(jsonPath("$.data[0].actorName").value("Dev"))
                .andExpect(jsonPath("$.data[0].newValue.content").value("On it"))
                .andExpect(jsonPath("$.data[1].action").value("ASSIGNED"))
                .andExpect(jsonPath("$.data[1].newValue.userId").value(team.dev().userId().toString()))
                // §33: the status's name and category travel with its id.
                .andExpect(jsonPath("$.data[2].action").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.data[2].oldValue.statusName").value("To Do"))
                .andExpect(jsonPath("$.data[2].newValue.statusName").value("In Progress"))
                .andExpect(jsonPath("$.data[2].newValue.category").value("IN_PROGRESS"))
                // Only what moved: the untouched description is not in the entry.
                .andExpect(jsonPath("$.data[3].action").value("UPDATED"))
                .andExpect(jsonPath("$.data[3].oldValue.title").value("Draft"))
                .andExpect(jsonPath("$.data[3].newValue.title").value("Final"))
                .andExpect(jsonPath("$.data[3].oldValue.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.data[3].newValue.priority").value("HIGH"))
                .andExpect(jsonPath("$.data[3].newValue.description").doesNotExist())
                .andExpect(jsonPath("$.data[4].action").value("CREATED"))
                .andExpect(jsonPath("$.data[4].entityType").value("ISSUE"))
                .andExpect(jsonPath("$.data[4].newValue.title").value("Draft"));
    }

    @Test
    void aLinkShowsInBothIssuesHistories() throws Exception {
        Team team = newTeam();
        String blocker = createIssue(team, "Blocker");
        String blocked = createIssue(team, "Blocked");
        mockMvc.perform(post("/api/v1/issues/" + blocker + "/links")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIssueId\":\"" + blocked + "\",\"type\":\"BLOCKS\"}"))
                .andExpect(status().isCreated());

        history(team.admin(), blocker)
                .andExpect(jsonPath("$.data[0].action").value("LINKED"))
                .andExpect(jsonPath("$.data[0].newValue.type").value("BLOCKS"))
                .andExpect(jsonPath("$.data[0].newValue.targetIssueId").value(blocked));
        history(team.admin(), blocked)
                .andExpect(jsonPath("$.data[0].action").value("LINKED"))
                .andExpect(jsonPath("$.data[0].newValue.sourceIssueId").value(blocker));
    }

    @Test
    void losingAccessIsRecordedAsAnUnassignmentByWhoeverCausedIt() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team, "Orphaned");
        assign(team.admin(), issueId, team.dev().userId());

        mockMvc.perform(delete("/api/v1/workspaces/" + team.workspaceId() + "/members/" + team.dev().userId())
                        .header("Authorization", bearer(team.admin())))
                .andExpect(status().isNoContent());

        history(team.admin(), issueId)
                .andExpect(jsonPath("$.data[0].action").value("UNASSIGNED"))
                .andExpect(jsonPath("$.data[0].oldValue.userId").value(team.dev().userId().toString()))
                .andExpect(jsonPath("$.data[0].newValue.reason").value("LOST_ACCESS"))
                .andExpect(jsonPath("$.data[0].actorId").value(team.admin().userId().toString()));

        String log = workspaceLog(team.admin(), team.workspaceId(), "")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> removals = JsonPath.read(log,
                "$.data[?(@.entityType == 'MEMBER' && @.action == 'DELETED')]");
        assertThat(removals).hasSize(1);
        assertThat(removals.get(0).get("entityId")).isEqualTo(team.dev().userId().toString());
    }

    @Test
    void eachLogIsGatedLikeThePageThatShowsIt() throws Exception {
        Team team = newTeam();
        String issueId = createIssue(team, "Visible");
        comment(team.dev(), issueId, "Hello");

        // 🔑 admins only, 👥 leads only, 👤 anyone who can open the issue.
        workspaceLog(team.dev(), team.workspaceId(), "").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/audit-log").header("Authorization", bearer(team.dev())))
                .andExpect(status().isForbidden());
        history(team.dev(), issueId).andExpect(status().isOk());

        Account outsider = signUp("Outsider");
        mockMvc.perform(get("/api/v1/issues/" + issueId + "/audit-log").header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/audit-log").header("Authorization", bearer(team.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[-1].entityType").value("PROJECT"))
                .andExpect(jsonPath("$.data[-1].action").value("CREATED"));

        // The workspace log narrowed to one person.
        String devOnly = workspaceLog(team.admin(), team.workspaceId(), "?actorId=" + team.dev().userId())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(devOnly, "$.data[*].actorId"))
                .containsOnly(team.dev().userId().toString())
                .isNotEmpty();
    }

    @Test
    void closingASprintRecordsTheCarryOverOnTheSprintAndOnEachIssue() throws Exception {
        Team team = newTeam();
        String unfinished = createIssue(team, "Unfinished");
        String sprintId = JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/sprints")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 1\",\"startDate\":\"2026-10-19\",\"endDate\":\"2026-11-02\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/issues/" + unfinished + "/move-to-sprint")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintId\":\"" + sprintId + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/start").header("Authorization", bearer(team.admin())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incompleteIssueAction\":\"MOVE_TO_BACKLOG\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/audit-log").header("Authorization", bearer(team.admin())))
                .andExpect(jsonPath("$.data[0].action").value("SPRINT_CLOSED"))
                .andExpect(jsonPath("$.data[0].newValue.carriedOver").value(1))
                .andExpect(jsonPath("$.data[0].newValue.incompleteIssueAction").value("MOVE_TO_BACKLOG"));

        history(team.admin(), unfinished)
                .andExpect(jsonPath("$.data[0].action").value("MOVED"))
                .andExpect(jsonPath("$.data[0].oldValue.sprintId").value(sprintId))
                .andExpect(jsonPath("$.data[0].newValue.sprintId").doesNotExist())
                .andExpect(jsonPath("$.data[0].newValue.reason").value("SPRINT_CLOSED"))
                .andExpect(jsonPath("$.data[1].action").value("MOVED"))
                .andExpect(jsonPath("$.data[1].newValue.sprintId").value(sprintId));
    }

    @Test
    void historyCannotBeRewrittenEvenFromInsideTheDatabase() throws Exception {
        Team team = newTeam();
        createIssue(team, "Permanent");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update audit_log set action = 'DELETED' where workspace_id = ?", team.workspaceId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from audit_log where workspace_id = ?", team.workspaceId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private record Account(UUID userId, String token) {}

    private record Team(Account admin, Account dev, UUID workspaceId, UUID projectId) {}

    private ResultActions history(Account account, String issueId) throws Exception {
        return mockMvc.perform(get("/api/v1/issues/" + issueId + "/audit-log").header("Authorization", bearer(account)));
    }

    private ResultActions workspaceLog(Account account, UUID workspaceId, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/audit-log" + query)
                .header("Authorization", bearer(account)));
    }

    private void edit(Account actor, String issueId, String json) throws Exception {
        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
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

    private String statusIn(Team team, String category) throws Exception {
        String body = mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/statuses")
                        .header("Authorization", bearer(team.admin())))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$.data[?(@.category == '" + category + "')].id").get(0);
    }

    private String createIssue(Team team, String title) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues")
                        .header("Authorization", bearer(team.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private Team newTeam() throws Exception {
        Account admin = signUp("Admin");
        Account dev = signUp("Dev");
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Audit " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, admin.userId()));
        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Audited\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        return new Team(admin, dev, workspaceId, projectId);
    }

    private Account signUp(String fullName) throws Exception {
        String email = "audit-" + shortId() + "@simplira.com";
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
