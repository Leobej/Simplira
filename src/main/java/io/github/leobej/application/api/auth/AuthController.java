package io.github.leobej.application.api.auth;

import io.github.leobej.application.api.auth.request.LoginRequest;
import io.github.leobej.application.api.auth.request.RegisterRequest;
import io.github.leobej.application.api.auth.response.AuthResponse;
import io.github.leobej.application.api.auth.response.UserResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.token.SessionContext;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.AuthService;
import io.github.leobej.domain.service.AuthService.LoginResult;
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
    private final RefreshTokenCookie refreshTokenCookie;

    public AuthController(AuthService authService, RefreshTokenCookie refreshTokenCookie) {
        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
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
