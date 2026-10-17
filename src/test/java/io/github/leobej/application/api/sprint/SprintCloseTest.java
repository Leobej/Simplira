package io.github.leobej.application.api.sprint;

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

// The three loops that could only be closed once issues existed: §29's incomplete-issue
// handling, and the migrate-before-delete rules for statuses and types.
@AutoConfigureMockMvc
class SprintCloseTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void closingSendsUnfinishedWorkBackToTheBacklogAndLeavesFinishedWorkBehind() throws Exception {
        Board board = newBoard();
        UUID sprintId = createSprint(board, "Sprint 1");
        String unfinished = createIssueOnSprint(board, sprintId, "Still going");
        String finished = createIssueOnSprint(board, sprintId, "All done");

        setStatus(board, finished, board.doneStatusId());
        start(board, sprintId);

        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incompleteIssueAction\":\"MOVE_TO_BACKLOG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // The unfinished one is back in the backlog rather than stranded on a closed board...
        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(unfinished));

        // ...and the finished one stays with the sprint it was completed in, for the history.
        mockMvc.perform(get("/api/v1/issues/" + finished)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.sprintId").value(sprintId.toString()));
    }

    @Test
    void closingCanCarryUnfinishedWorkIntoTheNextSprint() throws Exception {
        Board board = newBoard();
        UUID current = createSprint(board, "Sprint 1");
        UUID next = createSprint(board, "Sprint 2");
        String carried = createIssueOnSprint(board, current, "Rolls over");
        start(board, current);

        mockMvc.perform(post("/api/v1/sprints/" + current + "/close")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incompleteIssueAction\":\"MOVE_TO_SPRINT\",\"targetSprintId\":\""
                                + next + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/sprints/" + next + "/issues")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(carried));

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void whatCountsAsFinishedFollowsTheCategoryNotTheName() throws Exception {
        Board board = newBoard();
        String shippedStatus = createStatus(board, "Shipped", "DONE", "#16A34A");
        UUID sprintId = createSprint(board, "Sprint 1");
        String issueId = createIssueOnSprint(board, sprintId, "Finished under another name");

        setStatus(board, issueId, shippedStatus);
        start(board, sprintId);

        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incompleteIssueAction\":\"MOVE_TO_BACKLOG\"}"))
                .andExpect(status().isOk());

        // "Shipped" is nowhere in the code; its DONE category is what kept the issue in place.
        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.sprintId").value(sprintId.toString()));
    }

    @Test
    void aStatusHoldingIssuesNeedsSomewhereToSendThem() throws Exception {
        Board board = newBoard();
        String reviewStatus = createStatus(board, "In Review", "IN_PROGRESS", "#F59E0B");
        String issueId = createIssue(board, "Under review");
        setStatus(board, issueId, reviewStatus);

        // §33 rule 2: refusing beats guessing a destination.
        mockMvc.perform(delete("/api/v1/projects/" + board.projectId() + "/statuses/" + reviewStatus)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATUS_HAS_ISSUES"));

        mockMvc.perform(delete("/api/v1/projects/" + board.projectId() + "/statuses/" + reviewStatus
                        + "?migrateToStatusId=" + board.todoStatusId())
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.statusId").value(board.todoStatusId()));
    }

    @Test
    void aTypeInUseStaysUntilItsIssuesAreRetyped() throws Exception {
        Board board = newBoard();
        String spikeType = JsonPath.read(mockMvc.perform(
                                post("/api/v1/projects/" + board.projectId() + "/issue-types")
                                        .header("Authorization", "Bearer " + board.token())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"Spike\",\"color\":\"#8B5CF6\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id");

        String issueId = createIssue(board, "An experiment");
        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":\"" + spikeType + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/projects/" + board.projectId() + "/issue-types/" + spikeType)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ISSUE_TYPE_IN_USE"));

        // Retype it and the type becomes removable.
        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":\"" + board.taskTypeId() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/projects/" + board.projectId() + "/issue-types/" + spikeType)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isNoContent());
    }

    private record Board(String token, UUID projectId, String taskTypeId, String todoStatusId,
                         String doneStatusId) {}

    private void start(Board board, UUID sprintId) throws Exception {
        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/start")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isOk());
    }

    private void setStatus(Board board, String issueId, String statusId) throws Exception {
        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusId\":\"" + statusId + "\"}"))
                .andExpect(status().isOk());
    }

    private String createStatus(Board board, String name, String category, String color) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/statuses")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"category\":\"" + category
                                + "\",\"color\":\"" + color + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String createIssue(Board board, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/issues")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String createIssueOnSprint(Board board, UUID sprintId, String title) throws Exception {
        String issueId = createIssue(board, title);
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/move-to-sprint")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintId\":\"" + sprintId + "\"}"))
                .andExpect(status().isOk());
        return issueId;
    }

    private UUID createSprint(Board board, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/sprints")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"startDate\":\"2026-03-02\","
                                + "\"endDate\":\"2026-03-16\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private Board newBoard() throws Exception {
        String email = "close-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\","
                                + "\"fullName\":\"Close Tester\"}"))
                .andExpect(status().isCreated());

        String token = JsonPath.read(mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");

        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Close " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));

        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Closing Time\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));

        String types = mockMvc.perform(get("/api/v1/projects/" + projectId + "/issue-types")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String statuses = mockMvc.perform(get("/api/v1/projects/" + projectId + "/statuses")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        return new Board(token, projectId,
                JsonPath.<List<String>>read(types, "$.data[?(@.name=='Task')].id").get(0),
                JsonPath.<List<String>>read(statuses, "$.data[?(@.name=='To Do')].id").get(0),
                JsonPath.<List<String>>read(statuses, "$.data[?(@.name=='Done')].id").get(0));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
