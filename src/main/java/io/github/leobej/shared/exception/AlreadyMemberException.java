package io.github.leobej.shared.exception;

public class AlreadyMemberException extends RuntimeException {
    public AlreadyMemberException(String email) {
        super("'" + email + "' is already a member of this workspace");
    }
}
