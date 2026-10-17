package io.github.leobej.application.api.comment;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CommentFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Test
    void aCommentIsListedWithItsAuthorResolved() throws Exception {
        Team team = newTeam("PUBLIC");
        String issueId = createIssue(team);

        comment(team.admin(), issueId, "Reproduced on staging.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorName").value("Admin"))
                .andExpect(jsonPath("$.data.edited").value(false));

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/comments")
                        .header("Authorization", "Bearer " + team.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("Reproduced on staging."));
    }

    @Test
    void mentionsAreReadFromTheTokenAndCountedOnce() throws Exception {
        Team team = newTeam("PUBLIC");
        String issueId = createIssue(team);
        String devToken = "@[Dev](" + team.dev().userId() + ")";

        // Twice in the text, once in the mentions; a bare "@Dev" is just text.
        comment(team.admin(), issueId, devToken + " can you look? Thanks " + devToken + ". cc @Dev")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mentionedUserIds.length()").value(1))
                .andExpect(jsonPath("$.data.mentionedUserIds[0]").value(team.dev().userId().toString()));
    }

    @Test
    void mentioningSomeoneWhoCannotSeeTheProjectKeepsTheTextButNotTheMention() throws Exception {
        Team team = newTeam("PRIVATE");
        String issueId = createIssue(team);
        String content = "Looping in @[Dev](" + team.dev().userId() + ")";

        // Dev is in the workspace but not on this private project.
        comment(team.admin(), issueId, content)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value(content))
                .andExpect(jsonPath("$.data.mentionedUserIds.length()").value(0));
    }

    @Test
    void onlyTheAuthorCanEditAndEditingReReadsTheMentions() throws Exception {
        Team team = newTeam("PUBLIC");
        String issueId = createIssue(team);
        String commentId = JsonPath.read(comment(team.admin(), issueId, "@[Dev](" + team.dev().userId() + ") ping")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        // Not even someone else on the same issue may change what the author said.
        mockMvc.perform(patch("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + team.dev().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"rewritten\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + team.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Never mind, sorted it.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edited").value(true))
                // The mention went with the text that contained it.
                .andExpect(jsonPath("$.data.mentionedUserIds.length()").value(0));
    }

    @Test
    void theAuthorOrAWorkspaceAdminCanDelete() throws Exception {
        Team team = newTeam("PUBLIC");
        String issueId = createIssue(team);
        String devComment = JsonPath.read(comment(team.dev(), issueId, "Something regrettable")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        String adminComment = JsonPath.read(comment(team.admin(), issueId, "Admin note")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        // A developer cannot remove someone else's comment...
        mockMvc.perform(delete("/api/v1/comments/" + adminComment)
                        .header("Authorization", "Bearer " + team.dev().token()))
                .andExpect(status().isForbidden());

        // ...but an admin can moderate, which is the one step beyond "delete own".
        mockMvc.perform(delete("/api/v1/comments/" + devComment)
                        .header("Authorization", "Bearer " + team.admin().token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/issues/" + issueId + "/comments")
                        .header("Authorization", "Bearer " + team.admin().token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(adminComment));
    }

    private record Account(UUID userId, String token) {}

    private record Team(Account admin, Account dev, UUID projectId) {}

    private org.springframework.test.web.servlet.ResultActions comment(Account author, String issueId, String content)
            throws Exception {
        return mockMvc.perform(post("/api/v1/issues/" + issueId + "/comments")
                .header("Authorization", "Bearer " + author.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content.replace("\"", "\\\"") + "\"}"));
    }

    private String createIssue(Team team) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + team.projectId() + "/issues")
                        .header("Authorization", "Bearer " + team.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Discussed\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private Team newTeam(String visibility) throws Exception {
        Account admin = signUp("Admin");
        Account dev = signUp("Dev");
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + admin.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Comments " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        userWorkspaceRepository.save(UserWorkspace.join(dev.userId(), workspaceId, WorkspaceRole.DEVELOPER, admin.userId()));

        UUID projectId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                                .header("Authorization", "Bearer " + admin.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Talk\",\"visibility\":\"" + visibility + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        return new Team(admin, dev, projectId);
    }

    private Account signUp(String fullName) throws Exception {
        String email = "comment-" + shortId() + "@simplira.com";
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
