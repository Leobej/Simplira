package io.github.leobej.domain.model.issue;

// A template row, copied into a project's own issue types when the project is created.
public record DefaultIssueType(String name, String icon, String color, boolean isDefault, int sortOrder) {
}
