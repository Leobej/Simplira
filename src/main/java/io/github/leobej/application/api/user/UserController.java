package io.github.leobej.application.api.user;

import io.github.leobej.application.api.auth.response.UserResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.infrastructure.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@CurrentUser User user) {
        return ApiResponse.success(UserResponse.from(user));
    }
}
