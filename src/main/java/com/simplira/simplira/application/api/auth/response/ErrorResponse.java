package com.simplira.simplira.application.api.auth.response;

import java.util.List;

public record ErrorResponse(String code, String message, List<String> details) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }
}
