package io.github.leobej.application.api.user;

import io.github.leobej.application.api.auth.RefreshTokenCookie;
import io.github.leobej.application.api.auth.response.AuthResponse;
import io.github.leobej.application.api.auth.response.UserResponse;
import io.github.leobej.application.api.user.request.ChangePasswordRequest;
import io.github.leobej.application.api.user.request.UpdateProfileRequest;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.token.SessionContext;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.AuthService;
import io.github.leobej.domain.service.AuthService.LoginResult;
import io.github.leobej.domain.service.UserProfileService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileService userProfileService;
    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    public UserController(UserProfileService userProfileService,
                          AuthService authService,
                          RefreshTokenCookie refreshTokenCookie) {
        this.userProfileService = userProfileService;
        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
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
