package io.github.leobej.domain.model.issue;

// Bulk assignment adds rather than replaces, so already-assigned issues are counted, not errors.
public record BulkAssignResult(int requested, int assigned, int alreadyAssigned) {
}
