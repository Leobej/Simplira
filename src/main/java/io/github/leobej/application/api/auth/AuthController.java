package io.github.leobej.application.api.auth;

import io.github.leobej.application.api.auth.request.ConfirmEmailChangeRequest;
import io.github.leobej.application.api.auth.request.ForgotPasswordRequest;
import io.github.leobej.application.api.auth.request.LoginRequest;
import io.github.leobej.application.api.auth.request.RegisterRequest;
import io.github.leobej.application.api.auth.request.ResendVerificationRequest;
import io.github.leobej.application.api.auth.request.ResetPasswordRequest;
import io.github.leobej.application.api.auth.request.VerifyEmailRequest;
import io.github.leobej.application.api.auth.response.AuthResponse;
import io.github.leobej.application.api.auth.response.UserResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.token.SessionContext;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.AuthService;
import io.github.leobej.domain.service.AuthService.LoginResult;
import io.github.leobej.domain.service.EmailChangeService;
import io.github.leobej.domain.service.EmailVerificationService;
import io.github.leobej.domain.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final EmailChangeService emailChangeService;
    private final RefreshTokenCookie refreshTokenCookie;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService,
                          EmailChangeService emailChangeService,
                          RefreshTokenCookie refreshTokenCookie) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.emailChangeService = emailChangeService;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    // 🌐 Always 200 with nothing in it (§6): the same answer whether or not the address has an account.
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ApiResponse.success(null);
    }

    // 🌐 The emailed token is the credential - the link may well be opened on another device.
    @PostMapping("/confirm-email-change")
    public ApiResponse<UserResponse> confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request) {
        return ApiResponse.success(UserResponse.from(emailChangeService.confirmChange(request.token())));
    }

    // 🌐 The emailed token is the credential. Every session ends, so the client goes to the login page.
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    // 🌐 The token is the credential: whoever followed the link proved the address.
    @PostMapping("/verify-email")
    public ApiResponse<UserResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ApiResponse.success(UserResponse.from(emailVerificationService.verify(request.token())));
    }

    // 🌐 Always 200 with nothing in it: the same answer whether or not the address has an account.
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ApiResponse.success(null);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password(), request.fullName());
        return ApiResponse.success(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        LoginResult result = authService.login(request.email(), request.password(), sessionOf(httpRequest));
        return startSession(result, httpResponse);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginResult result = authService.refresh(refreshToken, sessionOf(httpRequest));
        return startSession(result, httpResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        authService.logout(refreshToken);
        refreshTokenCookie.clear(httpResponse);
    }

    // The refresh token goes back in the cookie, never in the body — the response shape is
    // identical for login and refresh.
    private ApiResponse<AuthResponse> startSession(LoginResult result, HttpServletResponse httpResponse) {
        refreshTokenCookie.attach(httpResponse, result.refreshToken(), result.refreshTokenLifetime());
        return ApiResponse.success(AuthResponse.of(
                result.accessToken(),
                result.expiresIn(),
                UserResponse.from(result.user())
        ));
    }

    private static SessionContext sessionOf(HttpServletRequest request) {
        return new SessionContext(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
    }
}
