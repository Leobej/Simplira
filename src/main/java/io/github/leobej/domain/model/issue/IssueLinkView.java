package io.github.leobej.domain.model.issue;

// A link as seen from one of its issues, with the issue at the other end loaded for display.
public record IssueLinkView(IssueLink link, Issue linkedIssue) {}
