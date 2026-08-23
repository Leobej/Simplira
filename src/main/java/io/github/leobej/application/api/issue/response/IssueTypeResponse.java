package io.github.leobej.application.api.issue.response;

import io.github.leobej.domain.model.issue.IssueType;

import java.util.UUID;

public record IssueTypeResponse(
        UUID id,
        UUID projectId,
        String name,
        String icon,
        String color,
        boolean isDefault,
        int sortOrder
) {
    public static IssueTypeResponse from(IssueType type) {
        return new IssueTypeResponse(
                type.getId(),
                type.getProjectId(),
                type.getName(),
                type.getIcon(),
                type.getColor(),
                type.isDefault(),
                type.getSortOrder()
        );
    }
}
