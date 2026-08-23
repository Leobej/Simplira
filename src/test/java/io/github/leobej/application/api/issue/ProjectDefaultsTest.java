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
class ProjectDefaultsTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aNewProjectArrivesWithTypesAndStatusesAlreadySeeded() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);

        // Without this seeding a brand new project could not hold a single issue.
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/issue-types")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Task"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].name").value("Bug"));

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/statuses")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("To Do"))
                .andExpect(jsonPath("$.data[0].category").value("TODO"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[2].category").value("DONE"));
    }

    @Test
    void twoProjectsCustomiseIndependently() throws Exception {
        Account admin = signUp();
        UUID first = createProject(admin);
        UUID second = createProject(admin);

        renameFirstStatus(admin, first, "In QA");

        // The copy is per project, so the second one is untouched.
        mockMvc.perform(get("/api/v1/projects/" + second + "/statuses")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.data[0].name").value("To Do"));
    }

    @Test
    void aStatusKeepsItsCategoryMeaningWhenRenamed() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        String statusId = statusIds(admin, projectId).get(1);   // In Progress

        mockMvc.perform(patch("/api/v1/projects/" + projectId + "/statuses/" + statusId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"In Review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("In Review"))
                // The label changed; what the system reads did not.
                .andExpect(jsonPath("$.data.category").value("IN_PROGRESS"));
    }

    @Test
    void everyCategoryKeepsAtLeastOneStatus() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        List<String> ids = statusIds(admin, projectId);

        // Done is the only status in category DONE, so removing it would leave the project with
        // no way to finish anything.
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/statuses/" + ids.get(2))
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_STATUS_IN_CATEGORY"));

        // Add a second DONE status and the original becomes removable.
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/statuses")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Shipped\",\"category\":\"DONE\",\"color\":\"#16A34A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.position").value(4));

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/statuses/" + ids.get(2))
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void theDefaultStatusCannotBeRemovedOrLeftUnset() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        List<String> ids = statusIds(admin, projectId);

        mockMvc.perform(patch("/api/v1/projects/" + projectId + "/statuses/" + ids.get(0))
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isDefault\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEFAULT_ISSUE_STATUS_REQUIRED"));

        // Handing the default to another status moves it rather than clearing it.
        mockMvc.perform(patch("/api/v1/projects/" + projectId + "/statuses/" + ids.get(1))
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isDefault\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/statuses")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(jsonPath("$.data[0].isDefault").value(false))
                .andExpect(jsonPath("$.data[1].isDefault").value(true));
    }

    @Test
    void reorderingSwapsPositionsWithoutCollidingOnTheUniqueIndex() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        List<String> ids = statusIds(admin, projectId);

        String reversed = "{\"statusIds\":[\"" + ids.get(2) + "\",\"" + ids.get(1) + "\",\""
                + ids.get(0) + "\"]}";

        mockMvc.perform(patch("/api/v1/projects/" + projectId + "/statuses/reorder")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Done"))
                .andExpect(jsonPath("$.data[0].position").value(1))
                .andExpect(jsonPath("$.data[2].name").value("To Do"))
                .andExpect(jsonPath("$.data[2].position").value(3));

        // A partial order would leave statuses holding positions the reorder wants to reuse.
        mockMvc.perform(patch("/api/v1/projects/" + projectId + "/statuses/reorder")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusIds\":[\"" + ids.get(0) + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INCOMPLETE_STATUS_ORDER"));
    }

    @Test
    void issueTypesAreUniqueByNameAndKeepADefault() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/issue-types")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bug\",\"color\":\"#E74C3C\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NAME_ALREADY_EXISTS"));

        String typeId = JsonPath.read(mockMvc.perform(post("/api/v1/projects/" + projectId + "/issue-types")
                                .header("Authorization", "Bearer " + admin.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Spike\",\"color\":\"#8B5CF6\",\"isDefault\":true}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id");

        // The new type took the default, so Task is no longer it.
        String types = mockMvc.perform(get("/api/v1/projects/" + projectId + "/issue-types")
                        .header("Authorization", "Bearer " + admin.token()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(types, "$.data[?(@.name=='Task')].isDefault"))
                .containsExactly(false);

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/issue-types/" + typeId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEFAULT_ISSUE_TYPE_REQUIRED"));
    }

    @Test
    void developersCanReadTheBoardSetupButNotChangeIt() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        Account stranger = signUp();

        // Not in the workspace at all, so the project itself is invisible.
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/statuses")
                        .header("Authorization", "Bearer " + stranger.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    private record Account(UUID userId, String token) {}

    private void renameFirstStatus(Account account, UUID projectId, String newName) throws Exception {
        String statusId = statusIds(account, projectId).get(0);
        mockMvc.perform(patch("/api/v1/projects/" + projectId + "/statuses/" + statusId)
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + newName + "\"}"))
                .andExpect(status().isOk());
    }

    private List<String> statusIds(Account account, UUID projectId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/projects/" + projectId + "/statuses")
                        .header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[*].id");
    }

    private UUID createProject(Account account) throws Exception {
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + account.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Defaults " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));

        String body = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Board " + shortId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private Account signUp() throws Exception {
        String email = "board-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\","
                                + "\"fullName\":\"Board Tester\"}"))
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
