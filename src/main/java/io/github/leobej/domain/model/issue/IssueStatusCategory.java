package io.github.leobej.domain.model.issue;

// Teams rename statuses freely; the category is the fixed meaning the system reads. Charts,
// sprint close and blocked detection all use this, never the display name (§33).
public enum IssueStatusCategory {
    TODO,
    IN_PROGRESS,
    DONE
}
