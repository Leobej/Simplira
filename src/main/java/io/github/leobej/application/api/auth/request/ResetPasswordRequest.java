package io.github.leobej.application.api.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The same password rule as registration: BCrypt ignores anything past 72 bytes.
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String newPassword
) {}
