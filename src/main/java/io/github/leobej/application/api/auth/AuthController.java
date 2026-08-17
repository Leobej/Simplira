package io.github.leobej.application.api.auth;

import io.github.leobej.application.api.auth.request.LoginRequest;
import io.github.leobej.application.api.auth.request.RegisterRequest;
import io.github.leobej.application.api.auth.response.AuthResponse;
import io.github.leobej.application.api.auth.response.UserResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password(), request.fullName());
        return ApiResponse.success(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = authService.login(request.email(), request.password());
        return ApiResponse.success(AuthResponse.of(
                result.accessToken(),
                result.expiresIn(),
                UserResponse.from(result.user())
        ));
    }
}