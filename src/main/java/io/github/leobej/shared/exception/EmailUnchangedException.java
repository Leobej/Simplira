package io.github.leobej.shared.exception;

public class EmailUnchangedException extends RuntimeException {
    public EmailUnchangedException() {
        super("That is already the address on this account");
    }
}
