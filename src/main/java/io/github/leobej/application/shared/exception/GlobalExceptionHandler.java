package io.github.leobej.application.shared.exception;

import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.application.shared.response.ErrorResponse;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import io.github.leobej.shared.exception.InvalidCredentialsException;
import io.github.leobej.shared.exception.AlreadyMemberException;
import io.github.leobej.shared.exception.BusinessRuleException;
import io.github.leobej.shared.exception.DuplicateNameException;
import io.github.leobej.shared.exception.IssueAssigneeNotFoundException;
import io.github.leobej.shared.exception.IssueStatusNotFoundException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.IssueTypeNotFoundException;
import io.github.leobej.shared.exception.IssueVersionConflictException;
import io.github.leobej.shared.exception.DuplicateInvitationException;
import io.github.leobej.shared.exception.ForbiddenException;
import io.github.leobej.shared.exception.InvitationNotFoundException;
import io.github.leobej.shared.exception.InvalidProjectKeyException;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(IssueAssigneeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleIssueAssigneeNotFound(IssueAssigneeNotFoundException ex) {
        return ApiResponse.failure(ErrorResponse.of("ISSUE_ASSIGNEE_NOT_FOUND", ex.getMessage()));
    }

    // Two routes to the same conflict: the service catching a stale version the client sent,
    // and Hibernate's @Version losing the race between two writes (§36).
    @ExceptionHandler({IssueVersionConflictException.class, ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleVersionConflict(Exception ex) {
        return ApiResponse.failure(ErrorResponse.of("ISSUE_VERSION_CONFLICT",
                "This issue was changed by someone else. Reload and reapply your edit."));
    }
}
