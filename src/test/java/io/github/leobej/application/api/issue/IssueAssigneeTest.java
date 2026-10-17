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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IssueAssigneeTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    // No add-member endpoint is involved in these tests, so teammates are seeded directly.
    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void assigningIsIdempotentAndShowsUpOnTheIssue() throws Exception {
        Team team = newTeam("PUBLIC");
        String issueId = createIssue(team, "Needs an owner");

        assign(team.lead(), issueId, team.dev().userId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Dev"));

        // A double-click or retried request changes nothing.
        assign(team.lead(), issueId, team.dev().userId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // Cards carry assignee ids, so a board needs no request per card.
        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andExpect(jsonPath("$.data.assigneeIds[0]").value(team.dev().userId().toString()));
    }

    @Test
    void unassigningIsIdempotentToo() throws Exception {
        Team team = newTeam("PUBLIC");
        String issueId = createIssue(team, "Handed back");
        assign(team.lead(), issueId, team.dev().userId()).andExpect(status().isOk());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/issues/" + issueId + "/assignees/" + team.dev().userId())
                            .header("Authorization", "Bearer " + team.lead().token()))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void nobodyCanBeAssignedWorkTheyCannotSee() throws Exception {
        Team team = newTeam("PRIVATE");
        String issueId = createIssue(team, "Confidential");

        // Dev is in the workspace but not on this private project...
        assign(team.lead(), issueId, team.dev().userId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ASSIGNEE_CANNOT_SEE_PROJECT"));

        // ...and a stranger is not even in the workspace.
        Account stranger = signUp("Stranger");
        assign(team.lead(), issueId, stranger.userId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ASSIGNEE_CANNOT_SEE_PROJECT"));

        // Put Dev on the project and the same assignment goes through.
        mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/members")
                        .header("Authorization", "Bearer " + team.lead().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + team.dev().userId() + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated());

        assign(team.lead(), issueId, team.dev().userId()).andExpect(status().isOk());
    }

    @Test
    void theBoardFiltersByAssigneeAndByNobody() throws Exception {
        Team team = newTeam("PUBLIC");
        String mine = createIssue(team, "Assigned to Dev");
        String orphan = createIssue(team, "Nobody yet");
        assign(team.lead(), mine, team.dev().userId()).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/issues?assigneeId=" + team.dev().userId())
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(mine));

        mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/issues?unassigned=true")
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(orphan));
    }

    @Test
    void bulkAssignAddsToEveryIssueAndCountsTheOnesAlreadyDone() throws Exception {
        Team team = newTeam("PUBLIC");
        String first = createIssue(team, "One");
        String second = createIssue(team, "Two");
        String third = createIssue(team, "Three");
        assign(team.lead(), first, team.dev().userId()).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues/bulk-assign")
                        .header("Authorization", "Bearer " + team.lead().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(team.dev().userId(), first, second, third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requested").value(3))
                .andExpect(jsonPath("$.data.assigned").value(2))
                .andExpect(jsonPath("$.data.alreadyAssigned").value(1));

        // Bulk operations are a team leader's tool (§1), not a developer's.
        mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues/bulk-assign")
                        .header("Authorization", "Bearer " + team.dev().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(team.dev().userId(), first)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aBulkAssignWithOneBadIdWritesNothing() throws Exception {
        Team team = newTeam("PUBLIC");
        String ours = createIssue(team, "Ours");
        Team elsewhere = newTeam("PUBLIC");
        String theirs = createIssue(elsewhere, "Theirs");

        mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues/bulk-assign")
                        .header("Authorization", "Bearer " + team.lead().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(team.dev().userId(), ours, theirs)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_NOT_FOUND"));

        // Validation runs before any write, so the valid id was not half-applied.
        String assignees = mockMvc.perform(get("/api/v1/issues/" + ours + "/assignees")
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(assignees, "$.data")).isEmpty();
    }

    private record Account(UUID userId, String token) {}

    private record Team(Account lead, Account dev, UUID projectId) {}

    private org.springframework.test.web.servlet.ResultActions assign(Account actor, String issueId, UUID userId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/issues/" + issueId + "/assignees")
                .header("Authorization", "Bearer " + actor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\"}"));
    }

    private static String bulkBody(UUID userId, String... issueIds) {
        return "{\"userId\":\"" + userId + "\",\"issueIds\":[\"" + String.join("\",\"", issueIds) + "\"]}";
    }

    private String createIssue(Team team, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues")
                        .header("Authorization", "Bearer " + team.lead().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private Team newTeam(String visibility) throws Exception {
        Account lead = signUp("Lead");
        Account dev = signUp("Dev");

        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + lead.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Assignees " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(
                UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, lead.userId()));

        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", "Bearer " + lead.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Owned Work\",\"visibility\":\"" + visibility + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));

        return new Team(lead, dev, projectId);
    }

    private Account signUp(String fullName) throws Exception {
        String email = "assign-" + shortId() + "@simplira.com";
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
