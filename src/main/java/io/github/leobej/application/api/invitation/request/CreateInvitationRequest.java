package io.github.leobej.application.api.invitation.request;

import io.github.leobej.domain.model.workspace.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInvitationRequest(
        @NotBlank @Email String email,
        @NotNull WorkspaceRole role
) {}
