package io.github.leobej.application.api.invitation;

import com.jayway.jsonpath.JsonPath;
import io.github.leobej.support.PostgresTestBase;
import io.github.leobej.support.RecordingEmailConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InvitationFlowTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailConfig.RecordingEmailService emails;

    @Test
    void anInvitedUserJoinsWithTheRoleTheyWereGiven() throws Exception {
        Account owner = signUp("Owner");
        Account invitee = signUp("Invitee");
        UUID workspaceId = createWorkspace(owner);

        invite(owner, workspaceId, invitee.email(), "TEAM_LEADER");

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header("Authorization", "Bearer " + invitee.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + emails.invitationTokenFor(invitee.email()) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TEAM_LEADER"))
                .andExpect(jsonPath("$.data.workspace.id").value(workspaceId.toString()));

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void acceptingAnInvitationConfirmsTheAddressItWasSentTo() throws Exception {
        Account owner = signUp("Owner");
        Account invitee = signUp("Invitee");
        UUID workspaceId = createWorkspace(owner);
        invite(owner, workspaceId, invitee.email(), "DEVELOPER");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + invitee.token()))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header("Authorization", "Bearer " + invitee.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + emails.invitationTokenFor(invitee.email()) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        // Clicking a link sent to that address is the same proof the verification flow asks for.
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + invitee.token()))
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    @Test
    void acceptingWithADifferentAccountJoinsButProvesNothing() throws Exception {
        Account owner = signUp("Owner");
        Account invitee = signUp("Invitee");
        Account bystander = signUp("Bystander");
        UUID workspaceId = createWorkspace(owner);
        invite(owner, workspaceId, invitee.email(), "DEVELOPER");

        // The token is a capability, so a forwarded link still works...
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header("Authorization", "Bearer " + bystander.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + emails.invitationTokenFor(invitee.email()) + "\"}"))
                .andExpect(status().isOk())
                // ...but it says nothing about the bystander's own address.
                .andExpect(jsonPath("$.data.emailVerified").value(false));
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        Account owner = signUp("Owner");
        Account invitee = signUp("Invitee");
        UUID workspaceId = createWorkspace(owner);
        invite(owner, workspaceId, invitee.email(), "DEVELOPER");
        String token = emails.invitationTokenFor(invitee.email());

        accept(invitee, token).andExpect(status().isOk());
        accept(invitee, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVITATION_NOT_USABLE"));
    }

    @Test
    void aCancelledInvitationCannotBeAccepted() throws Exception {
        Account owner = signUp("Owner");
        Account invitee = signUp("Invitee");
        UUID workspaceId = createWorkspace(owner);
        String invitationId = invite(owner, workspaceId, invitee.email(), "DEVELOPER");

        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/invitations/" + invitationId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNoContent());

        accept(invitee, emails.invitationTokenFor(invitee.email()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITATION_NOT_FOUND"));
    }

    @Test
    void theSameAddressCannotBeInvitedTwiceOrInvitedOnceItJoined() throws Exception {
        Account owner = signUp("Owner");
        Account invitee = signUp("Invitee");
        UUID workspaceId = createWorkspace(owner);
        invite(owner, workspaceId, invitee.email(), "DEVELOPER");

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + invitee.email() + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVITATION_ALREADY_SENT"));

        accept(invitee, emails.invitationTokenFor(invitee.email())).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + invitee.email() + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_A_MEMBER"));
    }

    @Test
    void ordinaryMembersCannotInviteAndTheListNeverLeaksTheToken() throws Exception {
        Account owner = signUp("Owner");
        Account invitee = signUp("Invitee");
        UUID workspaceId = createWorkspace(owner);
        invite(owner, workspaceId, invitee.email(), "DEVELOPER");
        accept(invitee, emails.invitationTokenFor(invitee.email())).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + invitee.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone-else@simplira.com\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        String listed = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(listed).contains("ACCEPTED");
        assertThat(listed).doesNotContain(emails.invitationTokenFor(invitee.email()));
        assertThat(listed).doesNotContain("token");
    }

    private record Account(String email, String token) {}

    private org.springframework.test.web.servlet.ResultActions accept(Account account, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/accept")
                .header("Authorization", "Bearer " + account.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private String invite(Account inviter, UUID workspaceId, String email, String role) throws Exception {
        String body = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + inviter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private UUID createWorkspace(Account account) throws Exception {
        String body = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Invites " + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private Account signUp(String fullName) throws Exception {
        String email = "inv-" + UUID.randomUUID().toString().substring(0, 8) + "@simplira.com";
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

        return new Account(email, JsonPath.read(body, "$.data.accessToken"));
    }
}
