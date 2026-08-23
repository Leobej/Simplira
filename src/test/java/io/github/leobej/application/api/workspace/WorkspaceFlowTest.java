package io.github.leobej.application.api.workspace;

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
class WorkspaceFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    // There is no "add member" endpoint until invitations exist, so a second member is seeded
    // through the repository rather than over HTTP.
    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void creatingAWorkspaceDerivesASlugAndMakesTheCreatorAdmin() throws Exception {
        Session owner = signUp("Owner One");

        String body = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Tech\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("acme-tech"))
                .andReturn().getResponse().getContentAsString();

        UUID workspaceId = UUID.fromString(JsonPath.read(body, "$.data.id"));
        assertThat(UUID.fromString(JsonPath.read(body, "$.data.ownerId"))).isEqualTo(owner.userId());

        mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + workspaceId + "')]").exists());

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("ADMIN"));
    }

    @Test
    void aTakenSlugIsSuffixedRatherThanRejected() throws Exception {
        Session owner = signUp("Owner Two");
        String name = "Collide " + shortId();

        String first = createWorkspace(owner, name);
        String second = createWorkspace(owner, name);

        assertThat(second).isEqualTo(first + "-2");
    }

    @Test
    void aNonMemberCannotTellTheWorkspaceExists() throws Exception {
        Session owner = signUp("Owner Three");
        Session stranger = signUp("Stranger");
        UUID workspaceId = createWorkspaceId(owner, "Private " + shortId());

        // 404 rather than 403: a 403 would confirm which workspace ids are real.
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + stranger.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    void ordinaryMembersCannotUpdateTheWorkspace() throws Exception {
        Session owner = signUp("Owner Four");
        Session member = signUp("Plain Member");
        UUID workspaceId = createWorkspaceId(owner, "Roles " + shortId());
        joinAs(workspaceId, member.userId(), WorkspaceRole.DEVELOPER, owner.userId());

        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed by a developer\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed by the admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed by the admin"));
    }

    @Test
    void anAdminCanPromoteAndRemoveAMember() throws Exception {
        Session owner = signUp("Owner Five");
        Session member = signUp("Promoted Member");
        UUID workspaceId = createWorkspaceId(owner, "Promote " + shortId());
        joinAs(workspaceId, member.userId(), WorkspaceRole.DEVELOPER, owner.userId());

        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId + "/members/" + member.userId() + "/role")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEAM_LEADER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TEAM_LEADER"));

        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/members/" + member.userId())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void theOwnerCanNeitherLeaveNorBeDemoted() throws Exception {
        Session owner = signUp("Owner Six");
        Session admin = signUp("Second Admin");
        UUID workspaceId = createWorkspaceId(owner, "Owner " + shortId());
        joinAs(workspaceId, admin.userId(), WorkspaceRole.ADMIN, owner.userId());

        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/members/me")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isForbidden());

        // Even another admin cannot demote the owner, which is what keeps one ADMIN guaranteed.
        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId + "/members/" + owner.userId() + "/role")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isForbidden());

        // A plain member may leave whenever they like.
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/members/me")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void memberSearchNeedsTwoCharacters() throws Exception {
        Session owner = signUp("Owner Seven");
        Session bob = signUp("Bobbie Builder");
        UUID workspaceId = createWorkspaceId(owner, "Search " + shortId());
        joinAs(workspaceId, bob.userId(), WorkspaceRole.DEVELOPER, owner.userId());

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members/search?q=b")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members/search?q=bobbie")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Bobbie Builder"));
    }

    @Test
    void deletingAWorkspaceFreesItsSlug() throws Exception {
        Session owner = signUp("Owner Eight");
        String name = "Reusable " + shortId();

        UUID first = createWorkspaceId(owner, name);
        String firstSlug = JsonPath.read(getWorkspace(owner, first), "$.data.slug");

        mockMvc.perform(delete("/api/v1/workspaces/" + first)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/" + first)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNotFound());

        // The whole point of the partial unique index (§21): a plain UNIQUE would force "-2" here.
        assertThat(createWorkspace(owner, name)).isEqualTo(firstSlug);
    }

    private record Session(UUID userId, String token) {}

    private Session signUp(String fullName) throws Exception {
        String email = "ws-" + shortId() + "@simplira.com";
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

        return new Session(UUID.fromString(JsonPath.read(body, "$.data.user.id")),
                JsonPath.read(body, "$.data.accessToken"));
    }

    private String createWorkspace(Session session, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private UUID createWorkspaceId(Session session, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private String getWorkspace(Session session, UUID workspaceId) throws Exception {
        return mockMvc.perform(get("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void joinAs(UUID workspaceId, UUID userId, WorkspaceRole role, UUID invitedBy) {
        userWorkspaceRepository.save(UserWorkspace.join(userId, workspaceId, role, invitedBy));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
