package io.github.leobej.shared.exception;

public class DuplicateInvitationException extends RuntimeException {
    public DuplicateInvitationException(String email) {
        super("An invitation to '" + email + "' is already pending for this workspace");
    }
}
