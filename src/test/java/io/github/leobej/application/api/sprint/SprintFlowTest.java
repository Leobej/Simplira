package io.github.leobej.application.api.sprint;

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

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SprintFlowTest extends PostgresTestBase {

    private static final LocalDate START = LocalDate.of(2026, 3, 2);
    private static final LocalDate END = LocalDate.of(2026, 3, 16);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void aSprintStartsPlannedAndRunsThroughToCompleted() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        UUID sprintId = createSprint(admin, projectId, "Sprint 1");

        mockMvc.perform(get("/api/v1/sprints/" + sprintId).header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.startDate").value("2026-03-02"));

        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/start")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incompleteIssueAction\":\"MOVE_TO_BACKLOG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void onlyOneSprintCanBeActiveAtATime() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        UUID first = createSprint(admin, projectId, "Sprint 1");
        UUID second = createSprint(admin, projectId, "Sprint 2");

        start(admin, first).andExpect(status().isOk());
        start(admin, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SPRINT_ALREADY_ACTIVE"));

        // Closing the first one releases the slot.
        close(admin, first).andExpect(status().isOk());
        start(admin, second).andExpect(status().isOk());
    }

    @Test
    void statusTransitionsRunInOneDirection() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        UUID sprintId = createSprint(admin, projectId, "Sprint 1");

        // A PLANNED sprint has not started, so there is nothing to close.
        close(admin, sprintId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_SPRINT_TRANSITION"));

        start(admin, sprintId).andExpect(status().isOk());
        start(admin, sprintId).andExpect(status().isConflict());

        // An active sprint is someone's current board; it has to be closed before deleting.
        mockMvc.perform(delete("/api/v1/sprints/" + sprintId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isConflict());

        close(admin, sprintId).andExpect(status().isOk());
        close(admin, sprintId).andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/sprints/" + sprintId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void aSprintEndingBeforeItStartsIsRejected() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/sprints")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backwards\",\"startDate\":\"2026-03-16\",\"endDate\":\"2026-03-02\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void closingIntoAnotherSprintChecksThatSprintFirst() throws Exception {
        Account admin = signUp();
        UUID projectId = createProject(admin);
        UUID sprintId = createSprint(admin, projectId, "Sprint 1");
        start(admin, sprintId).andExpect(status().isOk());

        // MOVE_TO_SPRINT without a target never reaches the service.
        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incompleteIssueAction\":\"MOVE_TO_SPRINT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        // A sprint belonging to another project is not a valid destination.
        UUID otherProject = createProject(admin);
        UUID foreignSprint = createSprint(admin, otherProject, "Elsewhere");

        mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incompleteIssueAction\":\"MOVE_TO_SPRINT\",\"targetSprintId\":\""
                                + foreignSprint + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPRINT_NOT_FOUND"));
    }

    @Test
    void developersCanReadSprintsButNotRunThem() throws Exception {
        Account admin = signUp();
        Account developer = signUp();
        UUID projectId = createProject(admin);
        joinWorkspace(workspaceOf(admin, projectId), developer.userId(), admin.userId());
        UUID sprintId = createSprint(admin, projectId, "Sprint 1");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/sprints")
                        .header("Authorization", "Bearer " + developer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        start(developer, sprintId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void sprintsOfAnInvisibleProjectAreInvisibleToo() throws Exception {
        Account admin = signUp();
        Account outsider = signUp();
        UUID projectId = createPrivateProject(admin);
        joinWorkspace(workspaceOf(admin, projectId), outsider.userId(), admin.userId());
        UUID sprintId = createSprint(admin, projectId, "Hidden");

        // SPRINT_NOT_FOUND rather than PROJECT_NOT_FOUND: the latter would confirm the sprint id
        // is real while hiding only its project.
        mockMvc.perform(get("/api/v1/sprints/" + sprintId)
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPRINT_NOT_FOUND"));
    }

    private record Account(UUID userId, String token) {}

    private org.springframework.test.web.servlet.ResultActions start(Account account, UUID sprintId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/start")
                .header("Authorization", "Bearer " + account.token()));
    }

    private org.springframework.test.web.servlet.ResultActions close(Account account, UUID sprintId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/sprints/" + sprintId + "/close")
                .header("Authorization", "Bearer " + account.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"incompleteIssueAction\":\"MOVE_TO_BACKLOG\"}"));
    }

    private UUID createSprint(Account account, UUID projectId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId + "/sprints")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"startDate\":\"" + START
                                + "\",\"endDate\":\"" + END + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private UUID createProject(Account account) throws Exception {
        return createProject(account, "PUBLIC");
    }

    private UUID createPrivateProject(Account account) throws Exception {
        return createProject(account, "PRIVATE");
    }

    private UUID createProject(Account account, String visibility) throws Exception {
        UUID workspaceId = createWorkspace(account);
        String body = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprints " + shortId() + "\",\"visibility\":\"" + visibility + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private UUID workspaceOf(Account account, UUID projectId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.workspaceId"));
    }

    private UUID createWorkspace(Account account) throws Exception {
        String body = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint WS " + shortId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private void joinWorkspace(UUID workspaceId, UUID userId, UUID invitedBy) {
        userWorkspaceRepository.save(
                UserWorkspace.join(userId, workspaceId, WorkspaceRole.DEVELOPER, invitedBy));
    }

    private Account signUp() throws Exception {
        String email = "sprint-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\","
                                + "\"fullName\":\"Sprint Tester\"}"))
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
