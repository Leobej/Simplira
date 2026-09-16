package io.github.leobej.application.api.issue;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IssueAssigneeFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void anAssignedPersonShowsOnTheIssueAndOnItsCard() throws Exception {
        Board board = newBoard();
        Account developer = joinAsDeveloper(board);
        String issueId = createIssue(board, "Fix the login button");

        mockMvc.perform(post("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + developer.userId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(developer.userId().toString()))
                .andExpect(jsonPath("$.data.fullName").value("Dana Developer"))
                .andExpect(jsonPath("$.data.assignedBy").value(board.userId().toString()));

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value(developer.email()));

        // The card carries ids only, so a board renders avatars without a call per issue.
        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.assigneeIds.length()").value(1))
                .andExpect(jsonPath("$.data.assigneeIds[0]").value(developer.userId().toString()));

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data[0].assigneeIds[0]").value(developer.userId().toString()));
    }

    @Test
    void anIssueTakesSeveralPeopleAndTheSameOneOnlyOnce() throws Exception {
        Board board = newBoard();
        Account first = joinAsDeveloper(board);
        Account second = joinAsDeveloper(board);
        String issueId = createIssue(board, "Pair on the migration");

        assign(board, issueId, first.userId());
        assign(board, issueId, second.userId());
        // Two people clicking the same avatar is a no-op for the slower one, not a 409.
        assign(board, issueId, first.userId());

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void someoneWhoCannotOpenTheProjectCannotBeGivenItsWork() throws Exception {
        Board board = newBoard(true);       // PRIVATE
        Account outsider = signUp("Olive Outsider");
        joinWorkspace(board.workspaceId(), outsider.userId(), board.userId());
        String issueId = createIssue(board, "Secret work");

        // In the workspace, but not on this private project: assigning them would put work on a
        // board they get a 404 on.
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + outsider.userId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ASSIGNEE_WITHOUT_PROJECT_ACCESS"));

        // A user id that belongs to nobody gets the same answer, so the endpoint cannot be used
        // to probe which accounts exist.
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ASSIGNEE_WITHOUT_PROJECT_ACCESS"));
    }

    @Test
    void unassigningWorksOnceAndThenSaysThereIsNothingToRemove() throws Exception {
        Board board = newBoard();
        Account developer = joinAsDeveloper(board);
        String issueId = createIssue(board, "Hand it back");
        assign(board, issueId, developer.userId());

        mockMvc.perform(delete("/api/v1/issues/" + issueId + "/assignees/" + developer.userId())
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/issues/" + issueId + "/assignees/" + developer.userId())
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_ASSIGNEE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.assigneeIds.length()").value(0));
    }

    @Test
    void theBoardFiltersByAssigneeAndByNobody() throws Exception {
        Board board = newBoard();
        Account developer = joinAsDeveloper(board);
        String mine = createIssue(board, "Mine");
        createIssue(board, "Nobody's");
        assign(board, mine, developer.userId());

        mockMvc.perform(get("/api/v1/projects/" + board.projectId()
                        + "/issues?assigneeId=" + developer.userId())
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(mine))
                .andExpect(jsonPath("$.meta.total").value(1));

        mockMvc.perform(get("/api/v1/projects/" + board.projectId() + "/issues?unassigned=true")
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Nobody's"));

        // Nobody else has any, so the filter matches nothing rather than everything.
        mockMvc.perform(get("/api/v1/projects/" + board.projectId()
                        + "/issues?assigneeId=" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + board.token()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void bulkAssignAddsWithoutRemovingAndTakesALeader() throws Exception {
        Board board = newBoard();
        Account developer = joinAsDeveloper(board);
        String first = createIssue(board, "One");
        String second = createIssue(board, "Two");
        assign(board, first, developer.userId());

        String body = "{\"issueIds\":[\"" + first + "\",\"" + second + "\"],"
                + "\"userIds\":[\"" + developer.userId() + "\"]}";

        // §1: a DEVELOPER assigns one issue at a time; rewriting a batch is a leader's call.
        mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/issues/bulk-assign")
                        .header("Authorization", "Bearer " + developer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/issues/bulk-assign")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issuesTouched").value(2))
                .andExpect(jsonPath("$.data.assignmentsCreated").value(1))
                .andExpect(jsonPath("$.data.alreadyAssigned").value(1));

        // Running it again is safe: nothing new, nothing lost.
        mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/issues/bulk-assign")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.data.assignmentsCreated").value(0))
                .andExpect(jsonPath("$.data.alreadyAssigned").value(2));
    }

    @Test
    void bulkAssignCannotReachIssuesInAnotherProject() throws Exception {
        Board board = newBoard();
        Board other = newBoard();
        String foreign = createIssue(other, "Somebody else's");

        mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/issues/bulk-assign")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueIds\":[\"" + foreign + "\"],"
                                + "\"userIds\":[\"" + board.userId() + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_NOT_FOUND"));
    }

    private record Board(UUID userId, String token, UUID workspaceId, UUID projectId) {}

    private record Account(UUID userId, String email, String token) {}

    private void assign(Board board, String issueId, UUID userId) throws Exception {
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isCreated());
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

    // A second person in the workspace and on the project, which is what makes them assignable.
    private Account joinAsDeveloper(Board board) throws Exception {
        Account account = signUp("Dana Developer");
        joinWorkspace(board.workspaceId(), account.userId(), board.userId());

        mockMvc.perform(post("/api/v1/projects/" + board.projectId() + "/members")
                        .header("Authorization", "Bearer " + board.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + account.userId() + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated());
        return account;
    }

    private void joinWorkspace(UUID workspaceId, UUID userId, UUID invitedBy) {
        userWorkspaceRepository.save(
                UserWorkspace.join(userId, workspaceId, WorkspaceRole.DEVELOPER, invitedBy));
    }

    private Board newBoard() throws Exception {
        return newBoard(false);
    }

    private Board newBoard(boolean privateProject) throws Exception {
        Account owner = signUp("Ada Admin");

        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + owner.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Assignees " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));

        String project = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Simplira Backend\""
                                + (privateProject ? ",\"visibility\":\"PRIVATE\"" : "") + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return new Board(owner.userId(), owner.token(), workspaceId,
                UUID.fromString(JsonPath.read(project, "$.data.id")));
    }

    private Account signUp(String fullName) throws Exception {
        String email = "assignee-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\","
                                + "\"fullName\":\"" + fullName + "\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return new Account(UUID.fromString(JsonPath.read(body, "$.data.user.id")), email,
                JsonPath.read(body, "$.data.accessToken"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
