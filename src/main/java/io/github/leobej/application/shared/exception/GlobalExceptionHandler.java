package io.github.leobej.application.shared.exception;

import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.application.shared.response.ErrorResponse;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import io.github.leobej.shared.exception.InvalidCredentialsException;
import io.github.leobej.shared.exception.AlreadyMemberException;
import io.github.leobej.shared.exception.DuplicateInvitationException;
import io.github.leobej.shared.exception.ForbiddenException;
import io.github.leobej.shared.exception.InvitationNotFoundException;
import io.github.leobej.shared.exception.InvitationNotUsableException;
import io.github.leobej.shared.exception.InvalidRefreshTokenException;
import io.github.leobej.shared.exception.SlugAlreadyExistsException;
import io.github.leobej.shared.exception.WorkspaceMemberNotFoundException;
import io.github.leobej.shared.exception.WorkspaceNotFoundException;
import org.springframework.http.HttpStatus;
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
}
