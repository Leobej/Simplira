package com.simplira.simplira.application.api.auth;

import com.simplira.simplira.application.api.auth.request.RegisterRequest;
import com.simplira.simplira.application.api.auth.response.UserResponse;
import com.simplira.simplira.application.shared.response.ApiResponse;
import com.simplira.simplira.domain.model.user.User;
import com.simplira.simplira.domain.service.AuthService;
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
}