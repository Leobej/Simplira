package io.github.leobej.application.api.user;

import io.github.leobej.application.api.auth.RefreshTokenCookie;
import io.github.leobej.application.api.auth.response.AuthResponse;
import io.github.leobej.application.api.auth.response.UserResponse;
import io.github.leobej.application.api.user.request.ChangeEmailRequest;
import io.github.leobej.application.api.user.request.ChangePasswordRequest;
import io.github.leobej.application.api.user.request.UpdateProfileRequest;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.token.SessionContext;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.AuthService;
import io.github.leobej.domain.service.AuthService.LoginResult;
import io.github.leobej.domain.service.EmailChangeService;
import io.github.leobej.domain.service.UserProfileService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileService userProfileService;
    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;
    private final EmailChangeService emailChangeService;

    public UserController(UserProfileService userProfileService,
                          AuthService authService,
                          RefreshTokenCookie refreshTokenCookie,
                          EmailChangeService emailChangeService) {
        this.userProfileService = userProfileService;
        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
        this.emailChangeService = emailChangeService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@CurrentUser User user) {
        return ApiResponse.success(UserResponse.from(user));
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                   @CurrentUser User user) {
        return ApiResponse.success(UserResponse.from(userProfileService.rename(user.getId(), request.fullName())));
    }

    // 202: nothing changes until the link sent to the new address is followed
    // (POST /auth/confirm-email-change).
    @PostMapping("/me/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> requestEmailChange(@Valid @RequestBody ChangeEmailRequest request,
                                                @CurrentUser User user) {
        emailChangeService.requestChange(user.getId(), request.newEmail(), request.currentPassword());
        return ApiResponse.success(null);
    }

    // multipart/form-data with the image in a part named "file"; up to 2 MB, PNG, JPEG, GIF or WebP.
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponse> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                  @CurrentUser User user) throws IOException {
        return ApiResponse.success(UserResponse.from(userProfileService.changeAvatar(user.getId(), file.getBytes())));
    }

    @DeleteMapping("/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar(@CurrentUser User user) {
        userProfileService.removeAvatar(user.getId());
    }

    // Every session ends and this one starts again, so the response is a login's: a new access
    // token in the body and a new refresh cookie.
    @PatchMapping("/me/password")
    public ApiResponse<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                    @CurrentUser User user,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        LoginResult result = authService.changePassword(user.getId(), request.currentPassword(),
                request.newPassword(),
                new SessionContext(httpRequest.getHeader(HttpHeaders.USER_AGENT), httpRequest.getRemoteAddr()));
        refreshTokenCookie.attach(httpResponse, result.refreshToken(), result.refreshTokenLifetime());
        return ApiResponse.success(AuthResponse.of(result.accessToken(), result.expiresIn(),
                UserResponse.from(result.user())));
    }
}
