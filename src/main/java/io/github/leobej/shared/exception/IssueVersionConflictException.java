package io.github.leobej.shared.exception;

public class IssueVersionConflictException extends RuntimeException {
    public IssueVersionConflictException() {
        super("This issue was changed by someone else. Reload and reapply your edit.");
    }
}
