package io.github.leobej.domain.model.issue;

public record DefaultIssueStatus(String name,
                                 IssueStatusCategory category,
                                 String color,
                                 int position,
                                 boolean isDefault) {
}
