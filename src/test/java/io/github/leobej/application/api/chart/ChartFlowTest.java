package io.github.leobej.application.api.chart;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ChartFlowTest extends PostgresTestBase {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void aBurndownCountsDownFromTheSprintsScopeAndKeepsWhatWasCarriedOut() throws Exception {
        Board board = newBoard();
        // Five days, started two days ago: two behind, today, two ahead.
        String sprintId = sprintWith(board, TODAY.minusDays(2), TODAY.plusDays(2), 3);
        moveTo(board, board.issues().get(0), "DONE");

        burndown(board, sprintId)
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.days.length()").value(5))
                .andExpect(jsonPath("$.data.days[0].date").value(TODAY.minusDays(2).toString()))
                .andExpect(jsonPath("$.data.days[0].remaining").value(3))
                .andExpect(jsonPath("$.data.days[0].ideal").value(3.0))
                .andExpect(jsonPath("$.data.days[2].remaining").value(2))
                .andExpect(jsonPath("$.data.days[2].completed").value(1))
                // Not happened yet: no actual line, only the ideal one.
                .andExpect(jsonPath("$.data.days[3].remaining").doesNotExist())
                .andExpect(jsonPath("$.data.days[4].ideal").value(0.0));

        // Closing sends the two unfinished issues to the backlog - they were still part of the sprint.
        close(board, sprintId);
        burndown(board, sprintId)
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.days[2].remaining").value(2));
    }

    @Test
    void velocityComparesWhatEachClosedSprintCommittedToWithWhatItFinished() throws Exception {
        Board board = newBoard();
        String sprintId = sprintWith(board, TODAY.minusDays(7), TODAY, 3);
        moveTo(board, board.issues().get(0), "DONE");
        close(board, sprintId);
        // Planned and never run: not velocity.
        createSprint(board, TODAY.plusDays(1), TODAY.plusDays(14));

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/charts/velocity").header("Authorization", board.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sprints.length()").value(1))
                .andExpect(jsonPath("$.data.sprints[0].sprintId").value(sprintId))
                .andExpect(jsonPath("$.data.sprints[0].committed").value(3))
                .andExpect(jsonPath("$.data.sprints[0].completed").value(1))
                .andExpect(jsonPath("$.data.averageCompleted").value(1.0));
    }

    @Test
    void cumulativeFlowCountsEachCategoryAtTheEndOfEachDay() throws Exception {
        Board board = newBoard();
        createIssues(board, 3);
        moveTo(board, board.issues().get(0), "DONE");
        moveTo(board, board.issues().get(1), "IN_PROGRESS");

        String range = "?from=" + TODAY.minusDays(1) + "&to=" + TODAY;
        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/charts/cumulative-flow" + range)
                        .header("Authorization", board.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(2))
                // Yesterday none of them existed yet.
                .andExpect(jsonPath("$.data.days[0].todo").value(0))
                .andExpect(jsonPath("$.data.days[0].done").value(0))
                .andExpect(jsonPath("$.data.days[1].todo").value(1))
                .andExpect(jsonPath("$.data.days[1].inProgress").value(1))
                .andExpect(jsonPath("$.data.days[1].done").value(1));

        // The default is the last 30 days, ending today.
        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/charts/cumulative-flow")
                        .header("Authorization", board.bearer()))
                .andExpect(jsonPath("$.data.days.length()").value(30))
                .andExpect(jsonPath("$.data.to").value(TODAY.toString()));

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/charts/cumulative-flow?from=" + TODAY + "&to="
                        + TODAY.minusDays(1)).header("Authorization", board.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/charts/cumulative-flow?from="
                        + TODAY.minusDays(200) + "&to=" + TODAY).header("Authorization", board.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chartsAreAsVisibleAsTheirProject() throws Exception {
        Board board = newBoard();
        String sprintId = createSprint(board, TODAY, TODAY.plusDays(7));
        Account outsider = signUp("Outsider");

        mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/charts/burndown").header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPRINT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/charts/velocity").header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isNotFound());
    }

    private ResultActions burndown(Board board, String sprintId) throws Exception {
        return mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/charts/burndown").header("Authorization", board.bearer()))
                .andExpect(status().isOk());
    }

    // A started sprint holding `issues` new issues.
    private String sprintWith(Board board, LocalDate start, LocalDate end, int issues) throws Exception {
        String sprintId = createSprint(board, start, end);
        for (String issueId : createIssues(board, issues)) {
            mockMvc.perform(post("/api/v1/issues/" + issueId + "/move-to-sprint")
                            .header("Authorization", board.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sprintId\":\"" + sprintId + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/start").header("Authorization", board.bearer()))
                .andExpect(status().isOk());
        return sprintId;
    }

    private String createSprint(Board board, LocalDate start, LocalDate end) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/sprints")
                        .header("Authorization", board.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint " + shortId() + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private void close(Board board, String sprintId) throws Exception {
        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                        .header("Authorization", board.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incompleteIssueAction\":\"MOVE_TO_BACKLOG\"}"))
                .andExpect(status().isOk());
    }

    private List<String> createIssues(Board board, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            board.issues().add(JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/issues")
                            .header("Authorization", board.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Charted " + i + "\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "$.data.id"));
        }
        return board.issues().subList(board.issues().size() - count, board.issues().size());
    }

    private void moveTo(Board board, String issueId, String category) throws Exception {
        String statuses = mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/statuses")
                        .header("Authorization", board.bearer()))
                .andReturn().getResponse().getContentAsString();
        String statusId = JsonPath.<List<String>>read(statuses, "$.data[?(@.category == '" + category + "')].id").get(0);
        mockMvc.perform(patch("/api/v1/issues/" + issueId)
                        .header("Authorization", board.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusId\":\"" + statusId + "\"}"))
                .andExpect(status().isOk());
    }

    private record Account(UUID userId, String token) {}

    private record Board(Account admin, UUID projectId, List<String> issues) {
        String bearer() {
            return "Bearer " + admin.token();
        }
    }

    private Board newBoard() throws Exception {
        Account admin = signUp("Admin");
        Account dev = signUp("Dev");
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + admin.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Charts " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, admin.userId()));
        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", "Bearer " + admin.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Charted\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        return new Board(admin, projectId, new java.util.ArrayList<>());
    }

    private Account signUp(String fullName) throws Exception {
        String email = "chart-" + shortId() + "@simplira.com";
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
