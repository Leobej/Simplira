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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "An assignee can see the project" is checked when an assignment is made. These tests hold it
// true afterwards, from every direction that can take access away - and confirm that losing one
// route to a project does not unassign someone who can still see it by another.
@AutoConfigureMockMvc
class AssignmentRevocationTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void leavingAWorkspaceDropsYourAssignmentsInIt() throws Exception {
        Setup s = setup(WorkspaceRole.DEVELOPER, "PUBLIC");
        String issueId = assignedIssue(s, s.other());

        mockMvc.perform(delete("/api/v1/workspaces/" + s.workspaceId() + "/members/me")
                        .header("Authorization", "Bearer " + s.other().token()))
                .andExpect(status().isNoContent());

        assertThat(assigneesOf(s, issueId)).isEmpty();
    }

    @Test
    void beingRemovedFromAWorkspaceDropsYourAssignmentsInIt() throws Exception {
        Setup s = setup(WorkspaceRole.DEVELOPER, "PUBLIC");
        String issueId = assignedIssue(s, s.other());

        mockMvc.perform(delete("/api/v1/workspaces/" + s.workspaceId() + "/members/" + s.other().userId())
                        .header("Authorization", "Bearer " + s.owner().token()))
                .andExpect(status().isNoContent());

        assertThat(assigneesOf(s, issueId)).isEmpty();
    }

    @Test
    void leavingAPrivateProjectDropsAssignmentsButLeavingAPublicOneDoesNot() throws Exception {
        Setup s = setup(WorkspaceRole.DEVELOPER, "PRIVATE");
        addToProject(s, s.other());
        String privateIssue = assignedIssue(s, s.other());

        removeFromProject(s, s.other());
        assertThat(assigneesOf(s, privateIssue)).isEmpty();

        // On a PUBLIC project the member list is not what grants access - the workspace is.
        UUID openProject = createProject(s, "PUBLIC");
        Setup open = new Setup(s.owner(), s.other(), s.workspaceId(), openProject);
        addToProject(open, s.other());
        String publicIssue = assignedIssue(open, s.other());

        removeFromProject(open, s.other());
        assertThat(assigneesOf(open, publicIssue)).containsExactly(s.other().userId().toString());
    }

    @Test
    void makingAProjectPrivateDropsOnlyThoseWhoLoseSightOfIt() throws Exception {
        Setup s = setup(WorkspaceRole.DEVELOPER, "PUBLIC");
        String issueId = assignedIssue(s, s.other());
        assign(s, issueId, s.owner());   // the creator is a project member and keeps access

        mockMvc.perform(patch("/api/v1/projects/" + s.projectId())
                        .header("Authorization", "Bearer " + s.owner().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());

        assertThat(assigneesOf(s, issueId)).containsExactly(s.owner().userId().toString());
    }

    @Test
    void aDemotedAdminLosesPrivateAssignmentsButKeepsPublicOnes() throws Exception {
        Setup s = setup(WorkspaceRole.ADMIN, "PRIVATE");
        // An admin sees every private project without being on it.
        String privateIssue = assignedIssue(s, s.other());

        UUID openProject = createProject(s, "PUBLIC");
        Setup open = new Setup(s.owner(), s.other(), s.workspaceId(), openProject);
        String publicIssue = assignedIssue(open, s.other());

        mockMvc.perform(patch("/api/v1/workspaces/" + s.workspaceId() + "/members/" + s.other().userId() + "/role")
                        .header("Authorization", "Bearer " + s.owner().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isOk());

        assertThat(assigneesOf(s, privateIssue)).isEmpty();
        assertThat(assigneesOf(open, publicIssue)).containsExactly(s.other().userId().toString());
    }

    private record Account(UUID userId, String token) {}

    private record Setup(Account owner, Account other, UUID workspaceId, UUID projectId) {}

    private String assignedIssue(Setup s, Account assignee) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + s.projectId() + "/issues")
                        .header("Authorization", "Bearer " + s.owner().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Somebody's job\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String issueId = JsonPath.read(body, "$.data.id");
        assign(s, issueId, assignee);
        return issueId;
    }

    private void assign(Setup s, String issueId, Account assignee) throws Exception {
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/assignees")
                        .header("Authorization", "Bearer " + s.owner().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + assignee.userId() + "\"}"))
                .andExpect(status().isOk());
    }

    private List<String> assigneesOf(Setup s, String issueId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + s.owner().token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.assigneeIds");
    }

    private void addToProject(Setup s, Account member) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + s.projectId() + "/members")
                        .header("Authorization", "Bearer " + s.owner().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + member.userId() + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated());
    }

    private void removeFromProject(Setup s, Account member) throws Exception {
        mockMvc.perform(delete("/api/v1/projects/" + s.projectId() + "/members/" + member.userId())
                        .header("Authorization", "Bearer " + s.owner().token()))
                .andExpect(status().isNoContent());
    }

    private Setup setup(WorkspaceRole otherRole, String visibility) throws Exception {
        Account owner = signUp();
        Account other = signUp();
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + owner.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Revoke " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(other.userId(), workspaceId, otherRole, owner.userId()));

        Setup partial = new Setup(owner, other, workspaceId, null);
        return new Setup(owner, other, workspaceId, createProject(partial, visibility));
    }

    private UUID createProject(Setup s, String visibility) throws Exception {
        return UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + s.workspaceId() + "/projects")
                                .header("Authorization", "Bearer " + s.owner().token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Project " + shortId() + "\",\"visibility\":\""
                                        + visibility + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
    }

    private Account signUp() throws Exception {
        String email = "revoke-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\","
                                + "\"fullName\":\"Revoke Tester\"}"))
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
