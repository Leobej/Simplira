package io.github.leobej.application.shared.exception;

import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.application.shared.response.ErrorResponse;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import io.github.leobej.shared.exception.InvalidCredentialsException;
import io.github.leobej.shared.exception.AlreadyMemberException;
import io.github.leobej.shared.exception.BusinessRuleException;
import io.github.leobej.shared.exception.CommentNotFoundException;
import io.github.leobej.shared.exception.DuplicateNameException;
import io.github.leobej.shared.exception.IssueStatusNotFoundException;
import io.github.leobej.shared.exception.IssueLinkNotFoundException;
import io.github.leobej.shared.exception.AttachmentNotFoundException;
import io.github.leobej.shared.exception.EmailNotVerifiedException;
import io.github.leobej.shared.exception.EmailUnchangedException;
import io.github.leobej.shared.exception.InvalidAttachmentException;
import io.github.leobej.shared.exception.InvalidCurrentPasswordException;
import io.github.leobej.shared.exception.InvalidDateRangeException;
import io.github.leobej.shared.exception.InvalidEmailChangeTokenException;
import io.github.leobej.shared.exception.InvalidResetTokenException;
import io.github.leobej.shared.exception.InvalidVerificationTokenException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.NotificationNotFoundException;
import io.github.leobej.shared.exception.IssueTypeNotFoundException;
import io.github.leobej.shared.exception.IssueVersionConflictException;
import io.github.leobej.shared.exception.DuplicateInvitationException;
import io.github.leobej.shared.exception.ForbiddenException;
import io.github.leobej.shared.exception.InvitationNotFoundException;
import io.github.leobej.shared.exception.InvalidProjectKeyException;
import io.github.leobej.shared.exception.LabelNotFoundException;
import io.github.leobej.shared.exception.InvitationNotUsableException;
import io.github.leobej.shared.exception.ProjectKeyAlreadyExistsException;
import io.github.leobej.shared.exception.ProjectMemberNotFoundException;
import io.github.leobej.shared.exception.ProjectNotFoundException;
import io.github.leobej.shared.exception.InvalidSprintDatesException;
import io.github.leobej.shared.exception.InvalidSprintTransitionException;
import io.github.leobej.shared.exception.SprintAlreadyActiveException;
import io.github.leobej.shared.exception.SprintNotFoundException;
import io.github.leobej.shared.exception.InvalidRefreshTokenException;
import io.github.leobej.shared.exception.SlugAlreadyExistsException;
import io.github.leobej.shared.exception.WorkspaceMemberNotFoundException;
import io.github.leobej.shared.exception.WorkspaceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleEmailExists(EmailAlreadyExistsException ex) {
        return ApiResponse.failure(ErrorResponse.of("EMAIL_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        return ApiResponse.failure(
                new ErrorResponse("VALIDATION_FAILED", "Request validation failed", details));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_CREDENTIALS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_REFRESH_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleWorkspaceNotFound(WorkspaceNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("WORKSPACE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleMemberNotFound(WorkspaceMemberNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("MEMBER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(ForbiddenException ex) {
        return ApiResponse.failure(ErrorResponse.of("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(SlugAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleSlugTaken(SlugAlreadyExistsException ex) {
        return ApiResponse.failure(ErrorResponse.of("SLUG_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleInvitationNotFound(InvitationNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVITATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvitationNotUsableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInvitationNotUsable(InvitationNotUsableException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVITATION_NOT_USABLE", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateInvitationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateInvitation(DuplicateInvitationException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVITATION_ALREADY_SENT", ex.getMessage()));
    }

    @ExceptionHandler(AlreadyMemberException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleAlreadyMember(AlreadyMemberException ex) {
        return ApiResponse.failure(ErrorResponse.of("ALREADY_A_MEMBER", ex.getMessage()));
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleProjectNotFound(ProjectNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("PROJECT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProjectMemberNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleProjectMemberNotFound(ProjectMemberNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("PROJECT_MEMBER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProjectKeyAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleProjectKeyTaken(ProjectKeyAlreadyExistsException ex) {
        return ApiResponse.failure(ErrorResponse.of("PROJECT_KEY_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidProjectKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidProjectKey(InvalidProjectKeyException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_PROJECT_KEY", ex.getMessage()));
    }

    @ExceptionHandler(SprintNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleSprintNotFound(SprintNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("SPRINT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(SprintAlreadyActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleSprintAlreadyActive(SprintAlreadyActiveException ex) {
        return ApiResponse.failure(ErrorResponse.of("SPRINT_ALREADY_ACTIVE", ex.getMessage()));
    }

    @ExceptionHandler(InvalidSprintTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInvalidSprintTransition(InvalidSprintTransitionException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_SPRINT_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(InvalidSprintDatesException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidSprintDates(InvalidSprintDatesException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_SPRINT_DATES", ex.getMessage()));
    }

    @ExceptionHandler(IssueTypeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleIssueTypeNotFound(IssueTypeNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("ISSUE_TYPE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IssueStatusNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleIssueStatusNotFound(IssueStatusNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("ISSUE_STATUS_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateName(DuplicateNameException ex) {
        return ApiResponse.failure(ErrorResponse.of("NAME_ALREADY_EXISTS", ex.getMessage()));
    }

    // Carries its own code so a rule can be added without a handler each time.
    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleBusinessRule(BusinessRuleException ex) {
        return ApiResponse.failure(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(IssueNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleIssueNotFound(IssueNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("ISSUE_NOT_FOUND", ex.getMessage()));
    }

    // Two routes to the same conflict: the service catching a stale version the client sent,
    // and Hibernate's @Version losing the race between two writes (§36).
    @ExceptionHandler({IssueVersionConflictException.class, ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleVersionConflict(Exception ex) {
        return ApiResponse.failure(ErrorResponse.of("ISSUE_VERSION_CONFLICT",
                "This issue was changed by someone else. Reload and reapply your edit."));
    }

    @ExceptionHandler(CommentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleCommentNotFound(CommentNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("COMMENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(LabelNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleLabelNotFound(LabelNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("LABEL_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IssueLinkNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleIssueLinkNotFound(IssueLinkNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("ISSUE_LINK_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleAttachmentNotFound(AttachmentNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("ATTACHMENT_NOT_FOUND", ex.getMessage()));
    }

    // 413 for too big, 415 for not an allowed image, 400 for anything else wrong with the file.
    @ExceptionHandler(InvalidAttachmentException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAttachment(InvalidAttachmentException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "FILE_TOO_LARGE" -> HttpStatus.CONTENT_TOO_LARGE;
            case "INVALID_FILE_TYPE" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.failure(ErrorResponse.of(ex.getCode(), ex.getMessage())));
    }

    // Over spring.servlet.multipart's limit, refused before the request reaches a controller -
    // answered the same as the service's own size check.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public ApiResponse<Void> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ApiResponse.failure(ErrorResponse.of("FILE_TOO_LARGE", "Maximum file size is 10 MB"));
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidVerificationToken(InvalidVerificationTokenException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_VERIFICATION_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidDateRange(InvalidDateRangeException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_DATE_RANGE", ex.getMessage()));
    }

    @ExceptionHandler(InvalidEmailChangeTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidEmailChangeToken(InvalidEmailChangeTokenException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_EMAIL_CHANGE_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(EmailUnchangedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleEmailUnchanged(EmailUnchangedException ex) {
        return ApiResponse.failure(ErrorResponse.of("EMAIL_UNCHANGED", ex.getMessage()));
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidResetToken(InvalidResetTokenException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_RESET_TOKEN", ex.getMessage()));
    }

    // 400, not 401: the session is fine, the field is wrong - and a 401 would make a client log out.
    @ExceptionHandler(InvalidCurrentPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidCurrentPassword(InvalidCurrentPasswordException ex) {
        return ApiResponse.failure(ErrorResponse.of("INVALID_CURRENT_PASSWORD", ex.getMessage()));
    }

    // 403, not 401: the caller is authenticated, just not yet allowed this.
    @ExceptionHandler(EmailNotVerifiedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return ApiResponse.failure(ErrorResponse.of("EMAIL_NOT_VERIFIED", ex.getMessage()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("NOTIFICATION_NOT_FOUND", ex.getMessage()));
    }
}
