package io.github.leobej.application.api.label;

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
class LabelFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void aProjectsLabelsAreUniqueByNameAndManagedByLeads() throws Exception {
        Team team = newTeam();
        createLabel(team.lead(), team.projectId(), "bug").andExpect(status().isCreated());

        createLabel(team.lead(), team.projectId(), "bug")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NAME_ALREADY_EXISTS"));

        // Using labels is everyone's; defining the vocabulary is a lead's.
        createLabel(team.dev(), team.projectId(), "mine")
                .andExpect(status().isForbidden());
    }

    @Test
    void attachingIsIdempotentAndShowsUpOnTheCard() throws Exception {
        Team team = newTeam();
        String labelId = labelId(team, "bug");
        String issueId = createIssue(team, "Broken");

        for (int attempt = 0; attempt < 2; attempt++) {
            attach(team.dev(), issueId, labelId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("bug"));
        }

        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andExpect(jsonPath("$.data.labelIds[0]").value(labelId));
    }

    @Test
    void anIssueCannotWearAnotherProjectsLabel() throws Exception {
        Team team = newTeam();
        Team other = newTeam();
        String foreignLabel = labelId(other, "theirs");
        String issueId = createIssue(team, "Ours");

        attach(team.lead(), issueId, foreignLabel)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LABEL_NOT_FOUND"));
    }

    @Test
    void filteringByMoreThanOneLabelRequiresAllOfThem() throws Exception {
        Team team = newTeam();
        String bug = labelId(team, "bug");
        String urgent = labelId(team, "urgent");
        String both = createIssue(team, "Urgent bug");
        String justBug = createIssue(team, "Ordinary bug");
        createIssue(team, "Unlabelled");

        attach(team.lead(), both, bug).andExpect(status().isOk());
        attach(team.lead(), both, urgent).andExpect(status().isOk());
        attach(team.lead(), justBug, bug).andExpect(status().isOk());

        String bugs = mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/issues?labelId=" + bug)
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(bugs, "$.data[*].id")).containsExactlyInAnyOrder(both, justBug);

        mockMvc.perform(get("/api/v1/projects/" + team.projectId() + "/issues?labelId=" + bug + "&labelId=" + urgent)
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(both));
    }

    @Test
    void deletingALabelTakesItOffEveryIssueAndFreesTheName() throws Exception {
        Team team = newTeam();
        String bug = labelId(team, "bug");
        String issueId = createIssue(team, "Labelled");
        attach(team.lead(), issueId, bug).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/projects/" + team.projectId() + "/labels/" + bug)
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andExpect(status().isNoContent());

        // No issue is left wearing a label nobody can see or pick any more.
        mockMvc.perform(get("/api/v1/issues/" + issueId)
                        .header("Authorization", "Bearer " + team.lead().token()))
                .andExpect(jsonPath("$.data.labelIds.length()").value(0));

        // §21's partial index: the deleted label does not hold the name hostage.
        createLabel(team.lead(), team.projectId(), "bug").andExpect(status().isCreated());
    }

    private record Account(UUID userId, String token) {}

    private record Team(Account lead, Account dev, UUID projectId) {}

    private org.springframework.test.web.servlet.ResultActions createLabel(Account actor, UUID projectId, String name)
            throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectId + "/labels")
                .header("Authorization", "Bearer " + actor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"color\":\"#E74C3C\"}"));
    }

    private String labelId(Team team, String name) throws Exception {
        return JsonPath.read(createLabel(team.lead(), team.projectId(), name)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private org.springframework.test.web.servlet.ResultActions attach(Account actor, String issueId, String labelId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/issues/" + issueId + "/labels")
                .header("Authorization", "Bearer " + actor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"labelId\":\"" + labelId + "\"}"));
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

    private Team newTeam() throws Exception {
        Account lead = signUp();
        Account dev = signUp();
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + lead.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Labels " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, lead.userId()));

        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", "Bearer " + lead.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Tagged\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        return new Team(lead, dev, projectId);
    }

    private Account signUp() throws Exception {
        String email = "label-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\","
                                + "\"fullName\":\"Label Tester\"}"))
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
