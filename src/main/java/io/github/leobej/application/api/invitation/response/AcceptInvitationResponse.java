package io.github.leobej.application.api.invitation.response;

import io.github.leobej.application.api.workspace.response.WorkspaceResponse;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.service.InvitationService.AcceptResult;

public record AcceptInvitationResponse(
        WorkspaceResponse workspace,
        WorkspaceRole role,
        // True when accepting also confirmed the address, so the client can skip prompting.
        boolean emailVerified
) {
    public static AcceptInvitationResponse from(AcceptResult result) {
        return new AcceptInvitationResponse(
                WorkspaceResponse.from(result.workspace()),
                result.role(),
                result.emailVerified()
        );
    }
}
