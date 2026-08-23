package io.github.leobej.application.api.project;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProjectFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void theKeyIsDerivedFromTheNameAndTheCreatorLeadsTheProject() throws Exception {
        Account admin = signUp("Admin");
        UUID workspaceId = createWorkspace(admin);

        String body = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Simplira Backend\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.key").value("SB"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andReturn().getResponse().getContentAsString();

        UUID projectId = UUID.fromString(JsonPath.read(body, "$.data.id"));
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("TEAM_LEADER"));
    }

    @Test
    void aTakenKeyIsSuffixedRatherThanRejected() throws Exception {
        Account admin = signUp("Admin");
        UUID workspaceId = createWorkspace(admin);

        assertThat(createProject(admin, workspaceId, "Simplira Backend", null, null)).isEqualTo("SB");
        assertThat(createProject(admin, workspaceId, "Simplira Backend", null, null)).isEqualTo("SB2");
    }

    @Test
    void anExplicitKeyConflictsInsteadOfBeingChanged() throws Exception {
        Account admin = signUp("Admin");
        UUID workspaceId = createWorkspace(admin);
        createProject(admin, workspaceId, "Anything", "SIMP", null);

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"key\":\"SIMP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_KEY_ALREADY_EXISTS"));

        // Lowercase or too short never reaches the service - bean validation rejects it.
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"key\":\"simp\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aPrivateProjectIsInvisibleToWorkspaceMembersWhoAreNotOnIt() throws Exception {
        Account admin = signUp("Admin");
        Account developer = signUp("Developer");
        UUID workspaceId = createWorkspace(admin);
        joinWorkspace(workspaceId, developer.userId(), WorkspaceRole.DEVELOPER, admin.userId());

        UUID openProject = projectId(admin, workspaceId, "Open Work", "PUBLIC");
        UUID secretProject = projectId(admin, workspaceId, "Secret Work", "PRIVATE");

        // PUBLIC is visible to every workspace member...
        mockMvc.perform(get("/api/v1/projects/" + openProject)
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isOk());

        // ...PRIVATE answers 404 rather than 403, so it does not confirm it exists.
        mockMvc.perform(get("/api/v1/projects/" + secretProject)
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(openProject.toString()));

        // The workspace admin sees both without being an explicit project member.
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void addingSomeoneToAPrivateProjectLetsThemSeeIt() throws Exception {
        Account admin = signUp("Admin");
        Account developer = signUp("Developer");
        UUID workspaceId = createWorkspace(admin);
        joinWorkspace(workspaceId, developer.userId(), WorkspaceRole.DEVELOPER, admin.userId());
        UUID secretProject = projectId(admin, workspaceId, "Secret Work", "PRIVATE");

        mockMvc.perform(post("/api/v1/projects/" + secretProject + "/members")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + developer.userId() + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"));

        mockMvc.perform(get("/api/v1/projects/" + secretProject)
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isOk());

        // A developer on the project still cannot manage its membership.
        mockMvc.perform(delete("/api/v1/projects/" + secretProject + "/members/" + admin.userId())
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void projectMembershipCannotReachOutsideTheWorkspace() throws Exception {
        Account admin = signUp("Admin");
        Account outsider = signUp("Outsider");
        UUID workspaceId = createWorkspace(admin);
        UUID projectId = projectId(admin, workspaceId, "Closed Shop", "PUBLIC");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + outsider.userId() + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void onlyWorkspaceAdminsCanCreateOrDelete() throws Exception {
        Account admin = signUp("Admin");
        Account leader = signUp("Team Leader");
        UUID workspaceId = createWorkspace(admin);
        joinWorkspace(workspaceId, leader.userId(), WorkspaceRole.TEAM_LEADER, admin.userId());
        UUID projectId = projectId(admin, workspaceId, "Governed", "PUBLIC");

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + leader.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Not Allowed\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + leader.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingAProjectFreesItsKey() throws Exception {
        Account admin = signUp("Admin");
        UUID workspaceId = createWorkspace(admin);
        UUID projectId = projectId(admin, workspaceId, "Simplira Backend", "PUBLIC");

        mockMvc.perform(delete("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNotFound());

        // Partial unique index again: without it the key would come back as SB2.
        assertThat(createProject(admin, workspaceId, "Simplira Backend", null, null)).isEqualTo("SB");
    }

    private record Account(UUID userId, String token) {}

    private String createProject(Account account, UUID workspaceId, String name, String key, String visibility)
            throws Exception {
        return JsonPath.read(createProjectBody(account, workspaceId, name, key, visibility), "$.data.key");
    }

    private UUID projectId(Account account, UUID workspaceId, String name, String visibility) throws Exception {
        return UUID.fromString(JsonPath.read(
                createProjectBody(account, workspaceId, name, null, visibility), "$.data.id"));
    }

    private String createProjectBody(Account account, UUID workspaceId, String name, String key, String visibility)
            throws Exception {
        StringBuilder json = new StringBuilder("{\"name\":\"").append(name).append("\"");
        if (key != null) {
            json.append(",\"key\":\"").append(key).append("\"");
        }
        if (visibility != null) {
            json.append(",\"visibility\":\"").append(visibility).append("\"");
        }
        json.append("}");

        return mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID createWorkspace(Account account) throws Exception {
        String body = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projects " + shortId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private void joinWorkspace(UUID workspaceId, UUID userId, WorkspaceRole role, UUID invitedBy) {
        userWorkspaceRepository.save(UserWorkspace.join(userId, workspaceId, role, invitedBy));
    }

    private Account signUp(String fullName) throws Exception {
        String email = "proj-" + shortId() + "@simplira.com";
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
