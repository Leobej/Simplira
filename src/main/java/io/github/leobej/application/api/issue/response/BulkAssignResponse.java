package io.github.leobej.application.api.issue.response;

import io.github.leobej.domain.model.issue.BulkAssignResult;

public record BulkAssignResponse(int requested, int assigned, int alreadyAssigned) {
    public static BulkAssignResponse from(BulkAssignResult result) {
        return new BulkAssignResponse(result.requested(), result.assigned(), result.alreadyAssigned());
    }
}
