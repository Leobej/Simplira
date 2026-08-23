package io.github.leobej.application.api.sprint.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// PATCH semantics: a null field is left alone. Sending one date keeps the other.
public record UpdateSprintRequest(
        @Size(max = 100) String name,
        LocalDate startDate,
        LocalDate endDate
) {
    @AssertTrue(message = "endDate must not be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
