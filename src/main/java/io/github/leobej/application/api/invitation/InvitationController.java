package io.github.leobej.application.api.invitation;

import io.github.leobej.application.api.invitation.request.AcceptInvitationRequest;
import io.github.leobej.application.api.invitation.request.CreateInvitationRequest;
import io.github.leobej.application.api.invitation.response.AcceptInvitationResponse;
import io.github.leobej.application.api.invitation.response.InvitationResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.InvitationService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvitationResponse> invite(@PathVariable UUID workspaceId,
                                                  @Valid @RequestBody CreateInvitationRequest request,
                                                  @CurrentUser User user) {
        return ApiResponse.success(InvitationResponse.from(
                invitationService.invite(workspaceId, request.email(), request.role(), user.getId())));
    }

    @GetMapping("/workspaces/{workspaceId}/invitations")
    public ApiResponse<List<InvitationResponse>> list(@PathVariable UUID workspaceId,
                                                      @CurrentUser User user) {
        return ApiResponse.success(invitationService.list(workspaceId, user.getId()).stream()
                .map(InvitationResponse::from)
                .toList());
    }

    @DeleteMapping("/workspaces/{workspaceId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID workspaceId,
                       @PathVariable UUID invitationId,
                       @CurrentUser User user) {
        invitationService.cancel(workspaceId, invitationId, user.getId());
    }

    // Authenticated, not public: acceptance attaches the invitation to a concrete account, so
    // there has to be one. The invite link opens a page that registers or signs in first, then
    // calls this with the token.
    @PostMapping("/invitations/accept")
    public ApiResponse<AcceptInvitationResponse> accept(@Valid @RequestBody AcceptInvitationRequest request,
                                                        @CurrentUser User user) {
        return ApiResponse.success(AcceptInvitationResponse.from(
                invitationService.accept(request.token(), user)));
    }
}
