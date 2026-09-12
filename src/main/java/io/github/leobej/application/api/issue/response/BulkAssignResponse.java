package io.github.leobej.application.api.issue.response;

import io.github.leobej.domain.service.IssueService.BulkAssignResult;

// A summary rather than the issues themselves: a hundred issues would be a large payload for
// something the client follows with a list refresh anyway.
public record BulkAssignResponse(int issuesTouched, int assignmentsCreated, int alreadyAssigned) {
    public static BulkAssignResponse from(BulkAssignResult result) {
        return new BulkAssignResponse(result.issuesTouched(), result.assignmentsCreated(),
                result.alreadyAssigned());
    }
}
