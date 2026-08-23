package io.github.leobej.application.api.issue.request;

import java.util.UUID;

// The two cards the issue was dropped between. Both null puts it at the end.
public record RepositionIssueRequest(UUID previousIssueId, UUID nextIssueId) {}
