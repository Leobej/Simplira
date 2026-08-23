package io.github.leobej.application.api.issue;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IssueFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void issuesGetTheProjectDefaultsAndNumberInSequence() throws Exception {
        Board board = newBoard();

        String first = createIssue(board, "Fix the login button");
        assertThat(JsonPath.<Integer>read(first, "$.data.number")).isEqualTo(1);
        assertThat(JsonPath.<String>read(first, "$.data.identifier")).isEqualTo(board.key() + "-1");
        // No type or status was sent, so the project's defaults were used.
        assertThat(JsonPath.<String>read(first, "$.data.typeId")).isEqualTo(board.taskTypeId());
        assertThat(JsonPath.<String>read(first, "$.data.statusId")).isEqualTo(board.todoStatusId());
        assertThat(JsonPath.<String>read(first, "$.data.sprintId")).isNull();
        assertThat(JsonPath.<String>read(first, "$.data.priority")).isEqualTo("MEDIUM");

        assertThat(JsonPath.<Integer>read(createIssue(board, "Second"), "$.data.number")).isEqualTo(2);
    }

    @Test
    void anIssueIsReachableByItsIdentifier() throws Exception {
        Board board = newBoard();
        String issueId = JsonPath.read(createIssue(board, "Shareable"), "$.data.id");

        mockMvc.perform(get("/api/v1/workspaces/" + board.workspaceId() + "/issues/" + board.key() + "-1")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(issueId));

        // Lowercase is accepted, nonsense is not.
        mockMvc.perform(get("/api/v1/workspaces/" + board.workspaceId() + "/issues/"
                        + board.key().toLowerCase() + "-1")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspaces/" + board.workspaceId() + "/issues/" + board.key() + "-999")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_NOT_FOUND"));
    }

    @Test
    void aDeletedIssueNeverGivesItsNumberBack() throws Exception {
        Board board = newBoard();
        String issueId = JsonPath.read(createIssue(board, "Doomed"), "$.data.id");

        mockMvc.perform(delete("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isNoContent());

        // Not 2: identifiers appear in links and commit messages, so a number is never reused.
        assertThat(JsonPath.<Integer>read(createIssue(board, "Next"), "$.data.number")).isEqualTo(2);
    }

    @Test
    void reachingADoneStatusClosesTheIssueAndLeavingItReopens() throws Exception {
        Board board = newBoard();
        String issueId = JsonPath.read(createIssue(board, "Closes"), "$.data.id");

        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusId\":\"" + board.doneStatusId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.closedBy").value(board.userId().toString()));

        // Closing follows the category, not the name, so moving back out reopens it.
        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusId\":\"" + board.todoStatusId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closedAt").doesNotExist());
    }

    @Test
    void aStaleVersionIsRefusedRatherThanOverwriting() throws Exception {
        Board board = newBoard();
        String issueId = JsonPath.read(createIssue(board, "Contested"), "$.data.id");

        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First writer wins\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        // The second editor still holds version 0 and is told to reload rather than clobbering.
        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Second writer loses\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ISSUE_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.title").value("First writer wins"));
    }

    @Test
    void subtasksAreOneLevelDeepAndDieWithTheirParent() throws Exception {
        Board board = newBoard();
        String parentId = JsonPath.read(createIssue(board, "Parent"), "$.data.id");

        String subtask = mockMvc.perform(post("/api/v1/issues/" + parentId + "/subtasks")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Child\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentId").value(parentId))
                .andReturn().getResponse().getContentAsString();
        String subtaskId = JsonPath.read(subtask, "$.data.id");

        mockMvc.perform(post("/api/v1/issues/" + subtaskId + "/subtasks")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Grandchild\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUBTASK_DEPTH_EXCEEDED"));

        // Subtasks stay off the board listing; they belong to their parent.
        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));

        mockMvc.perform(delete("/api/v1/issues/" + parentId)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/issues/" + subtaskId)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void issuesMoveOntoASprintAndBackToTheBacklog() throws Exception {
        Board board = newBoard();
        String issueId = JsonPath.read(createIssue(board, "Travels"), "$.data.id");
        String sprintId = createSprint(board);

        mockMvc.perform(post("/api/v1/issues/" + issueId + "/move-to-sprint")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintId\":\"" + sprintId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sprintId").value(sprintId));

        mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/issues")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1));

        // ...and it leaves the backlog while it is on a board.
        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/api/v1/issues/" + issueId + "/move-to-backlog")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sprintId").doesNotExist());
    }

    @Test
    void droppingACardBetweenTwoOthersLandsBetweenThem() throws Exception {
        Board board = newBoard();
        String first = JsonPath.read(createIssue(board, "One"), "$.data.id");
        String second = JsonPath.read(createIssue(board, "Two"), "$.data.id");
        String third = JsonPath.read(createIssue(board, "Three"), "$.data.id");

        mockMvc.perform(patch("/api/v1/issues/" + third + "/position")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previousIssueId\":\"" + first + "\",\"nextIssueId\":\"" + second + "\"}"))
                .andExpect(status().isOk());

        // No renumbering of the column: the moved card took a value in the gap.
        String listed = mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(listed, "$.data[*].title"))
                .containsExactly("One", "Three", "Two");
    }

    @Test
    void listsFilterWithoutNeedingNewEndpoints() throws Exception {
        Board board = newBoard();
        createIssue(board, "Login button is misaligned");
        String urgent = JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/issues")
                                .header("Authorization", "Bearer " + board.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Database on fire\",\"priority\":\"CRITICAL\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues?priority=CRITICAL")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(urgent));

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues?q=login")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Login button is misaligned"));

        // Both are still TODO, so a DONE filter matches nothing rather than everything.
        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues?statusCategory=DONE")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues?statusCategory=TODO&pageSize=1")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.pageSize").value(1));
    }

    @Test
    void blockingAnIssueRecordsWhy() throws Exception {
        Board board = newBoard();
        String issueId = JsonPath.read(createIssue(board, "Waiting on infra"), "$.data.id");

        mockMvc.perform(post("/api/v1/issues/" + issueId + "/block")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Waiting on the staging database\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.blockedReason").value("Waiting on the staging database"));

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues?isBlocked=true")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/api/v1/issues/" + issueId + "/block")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.blockedReason").doesNotExist());
    }

    private record Board(UUID userId, String token, UUID workspaceId, UUID projectId, String key,
                         String taskTypeId, String todoStatusId, String doneStatusId) {}

    private String createIssue(Board board, String title) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/issues")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String createSprint(Board board) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/sprints")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 1\",\"startDate\":\"2026-03-02\","
                                + "\"endDate\":\"2026-03-16\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private Board newBoard() throws Exception {
        String email = "issue-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\","
                                + "\"fullName\":\"Issue Tester\"}"))
                .andExpect(status().isCreated());

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(JsonPath.read(login, "$.data.user.id"));
        String token = JsonPath.read(login, "$.data.accessToken");

        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Issues " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));

        String project = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Simplira Backend\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projectId = UUID.fromString(JsonPath.read(project, "$.data.id"));
        String key = JsonPath.read(project, "$.data.key");

        String types = mockMvc.perform(get("/api/v1/projects/" + projectId + "/issue-types")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String statuses = mockMvc.perform(get("/api/v1/projects/" + projectId + "/statuses")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        return new Board(userId, token, workspaceId, projectId, key,
                JsonPath.<List<String>>read(types, "$.data[?(@.name=='Task')].id").get(0),
                JsonPath.<List<String>>read(statuses, "$.data[?(@.name=='To Do')].id").get(0),
                JsonPath.<List<String>>read(statuses, "$.data[?(@.name=='Done')].id").get(0));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
