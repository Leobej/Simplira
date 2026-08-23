package io.github.leobej.application.api.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

// The refresh token never reaches JavaScript: HttpOnly keeps it out of the DOM, and the path
// keeps the browser from attaching it to every ordinary API call.
@Component
public class RefreshTokenCookie {

    public static final String NAME = "simplira_refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final boolean secure;

    public RefreshTokenCookie(@Value("${simplira.security.refresh-cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    public void attach(HttpServletResponse response, String rawToken, Duration lifetime) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(rawToken, lifetime).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(PATH)
                // CSRF is disabled because the API is bearer-token based, so this cookie is the
                // one thing a cross-site POST could exploit. SameSite=Strict is what stops it.
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();
    }
}
