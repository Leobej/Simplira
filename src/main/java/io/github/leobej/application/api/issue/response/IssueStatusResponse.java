package io.github.leobej.application.api.issue.response;

import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueStatusCategory;

import java.util.UUID;

public record IssueStatusResponse(
        UUID id,
        UUID projectId,
        String name,
        IssueStatusCategory category,
        String color,
        int position,
        boolean isDefault
) {
    public static IssueStatusResponse from(IssueStatus status) {
        return new IssueStatusResponse(
                status.getId(),
                status.getProjectId(),
                status.getName(),
                status.getCategory(),
                status.getColor(),
                status.getPosition(),
                status.isDefault()
        );
    }
}
