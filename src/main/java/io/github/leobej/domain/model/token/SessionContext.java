package io.github.leobej.domain.model.token;

// Where a session was created from, recorded so a user can recognise their own sessions later.
public record SessionContext(String deviceInfo, String ipAddress) {

    public static SessionContext unknown() {
        return new SessionContext(null, null);
    }
}
