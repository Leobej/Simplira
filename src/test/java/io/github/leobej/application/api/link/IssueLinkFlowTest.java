package io.github.leobej.application.api.link;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IssueLinkFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anIssueStaysBlockedUntilItsLastBlockerIsUnlinked() throws Exception {
        Board board = newBoard();
        String first = createIssue(board, "First blocker");
        String second = createIssue(board, "Second blocker");
        String blocked = createIssue(board, "Waiting");

        String firstLink = linkId(link(board, first, blocked, "BLOCKS").andExpect(status().isCreated()));
        String secondLink = linkId(link(board, second, blocked, "BLOCKS").andExpect(status().isCreated()));
        expectBlocked(board, blocked, true);
        // BLOCKS is directional: the blocker itself is free to move.
        expectBlocked(board, first, false);

        unlink(board, blocked, firstLink).andExpect(status().isNoContent());
        expectBlocked(board, blocked, true);

        unlink(board, blocked, secondLink).andExpect(status().isNoContent());
        expectBlocked(board, blocked, false);
    }

    @Test
    void aManualBlockAndABlockingLinkDoNotClearEachOther() throws Exception {
        Board board = newBoard();
        String blocker = createIssue(board, "Blocker");
        String issue = createIssue(board, "Doubly stuck");

        block(board, issue, "Waiting on the vendor");
        String linkId = linkId(link(board, blocker, issue, "BLOCKS").andExpect(status().isCreated()));

        // Removing the link leaves the vendor problem where it was.
        unlink(board, issue, linkId).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/issues/" + issue).header("Authorization", bearer(board)))
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.blockedReason").value("Waiting on the vendor"));

        // And lifting the manual block leaves a link's block where it was.
        link(board, blocker, issue, "BLOCKS").andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/issues/" + issue + "/block").header("Authorization", bearer(board)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.blockedReason").doesNotExist());
    }

    @Test
    void aLinkIsListedFromBothEnds() throws Exception {
        Board board = newBoard();
        String source = createIssue(board, "Login page");
        String target = createIssue(board, "Signup page");
        link(board, source, target, "RELATED_TO").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/issues/" + source + "/links").header("Authorization", bearer(board)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].linkedIssue.id").value(target))
                .andExpect(jsonPath("$.data[0].linkedIssue.identifier").value("LNK-2"))
                .andExpect(jsonPath("$.data[0].linkedIssue.title").value("Signup page"));

        mockMvc.perform(get("/api/v1/issues/" + target + "/links").header("Authorization", bearer(board)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sourceIssueId").value(source))
                .andExpect(jsonPath("$.data[0].linkedIssue.id").value(source));
    }

    @Test
    void aPairHasOneLinkOfEachTypeWhicheverWayRound() throws Exception {
        Board board = newBoard();
        String a = createIssue(board, "A");
        String b = createIssue(board, "B");

        link(board, a, b, "BLOCKS").andExpect(status().isCreated());
        link(board, a, b, "BLOCKS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ISSUE_LINK_ALREADY_EXISTS"));
        // The reverse would be a deadlock, so it counts as the same pair.
        link(board, b, a, "BLOCKS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ISSUE_LINK_ALREADY_EXISTS"));

        // A different kind of link between the same two is its own thing.
        link(board, a, b, "RELATED_TO").andExpect(status().isCreated());

        link(board, a, a, "RELATED_TO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ISSUE_LINK_TO_SELF"));
    }

    @Test
    void linksStayInsideAProject() throws Exception {
        Board board = newBoard();
        UUID otherProject = createProject(board, "Elsewhere", "ELS");
        String ours = createIssue(board, "Ours");
        // Visible to the same admin, but in another project.
        String theirs = createIssue(board.token(), otherProject, "Theirs");

        link(board, ours, theirs, "BLOCKS")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_NOT_FOUND"));
    }

    @Test
    void deletingTheBlockerFreesWhatItBlocked() throws Exception {
        Board board = newBoard();
        String blocker = createIssue(board, "Doomed");
        String blocked = createIssue(board, "Waiting");
        link(board, blocker, blocked, "BLOCKS").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/issues/" + blocker).header("Authorization", bearer(board)))
                .andExpect(status().isNoContent());

        expectBlocked(board, blocked, false);
        mockMvc.perform(get("/api/v1/issues/" + blocked + "/links").header("Authorization", bearer(board)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void aLinkIsRemovedOnlyThroughAnIssueItJoins() throws Exception {
        Board board = newBoard();
        String a = createIssue(board, "A");
        String b = createIssue(board, "B");
        String bystander = createIssue(board, "Bystander");
        String linkId = linkId(link(board, a, b, "RELATED_TO").andExpect(status().isCreated()));

        unlink(board, bystander, linkId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_LINK_NOT_FOUND"));

        // Either end will do.
        unlink(board, b, linkId).andExpect(status().isNoContent());
    }

    private record Board(String token, UUID workspaceId, UUID projectId) {}

    private ResultActions link(Board board, String source, String target, String type) throws Exception {
        return mockMvc.perform(post("/api/v1/issues/" + source + "/links")
                .header("Authorization", bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetIssueId\":\"" + target + "\",\"type\":\"" + type + "\"}"));
    }

    private ResultActions unlink(Board board, String issueId, String linkId) throws Exception {
        return mockMvc.perform(delete("/api/v1/issues/" + issueId + "/links/" + linkId)
                .header("Authorization", bearer(board)));
    }

    private static String linkId(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private void block(Board board, String issueId, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/issues/" + issueId + "/block")
                        .header("Authorization", bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk());
    }

    private void expectBlocked(Board board, String issueId, boolean blocked) throws Exception {
        mockMvc.perform(get("/api/v1/issues/" + issueId).header("Authorization", bearer(board)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(blocked));
    }

    private String createIssue(Board board, String title) throws Exception {
        return createIssue(board.token(), board.projectId(), title);
    }

    private String createIssue(String token, UUID projectId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId + "/issues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private Board newBoard() throws Exception {
        String token = signUp();
        UUID workspaceId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Links " + shortId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
        Board board = new Board(token, workspaceId, null);
        return new Board(token, workspaceId, createProject(board, "Linked", "LNK"));
    }

    private UUID createProject(Board board, String name, String key) throws Exception {
        return UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/v1/workspaces/" + board.workspaceId() + "/projects")
                                .header("Authorization", "Bearer " + board.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + name + "\",\"key\":\"" + key + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id"));
    }

    private String signUp() throws Exception {
        String email = "link-" + shortId() + "@simplira.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\","
                                + "\"fullName\":\"Link Tester\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private static String bearer(Board board) {
        return "Bearer " + board.token();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
