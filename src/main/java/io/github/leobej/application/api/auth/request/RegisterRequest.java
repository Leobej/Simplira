package io.github.leobej.application.api.auth.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        // BCrypt silently ignores anything past 72 bytes, so reject it instead of pretending it counts.
        @NotBlank @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String password,
        @NotBlank String fullName
) {}
