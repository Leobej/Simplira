package io.github.leobej.domain.model.sprint;

// What happens to issues that are still unfinished when a sprint closes (§29).
public enum IncompleteIssueAction {
    MOVE_TO_BACKLOG,
    MOVE_TO_SPRINT
}
