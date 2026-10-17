package io.github.leobej.application.api.sprint.request;

import io.github.leobej.domain.model.sprint.IncompleteIssueAction;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CloseSprintRequest(
        @NotNull IncompleteIssueAction incompleteIssueAction,
        UUID targetSprintId    // required only when action is MOVE_TO_SPRINT
) {
    @AssertTrue(message = "targetSprintId required when action is MOVE_TO_SPRINT")
    public boolean isTargetSprintIdValid() {
        if (incompleteIssueAction == IncompleteIssueAction.MOVE_TO_SPRINT) {
            return targetSprintId != null;
        }
        return true;
    }
}
