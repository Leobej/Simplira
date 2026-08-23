package io.github.leobej.domain.model.sprint;

import io.github.leobej.shared.exception.InvalidSprintDatesException;
import io.github.leobej.shared.exception.InvalidSprintTransitionException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Sprint {
    private final UUID id;
    private final UUID projectId;
    private String name;
    private SprintStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private final UUID createdBy;
    private final Instant createdAt;
    private Instant updatedAt;

    public static Sprint create(UUID projectId,
                                String name,
                                LocalDate startDate,
                                LocalDate endDate,
                                UUID createdBy) {
        requireOrderedDates(startDate, endDate);
        Instant now = Instant.now();
        return new Sprint(
                UUID.randomUUID(),   // domain owns identity
                projectId,
                name,
                SprintStatus.PLANNED,
                startDate,
                endDate,
                createdBy,
                now,
                now
        );
    }

    public void rename(String newName) {
        this.name = newName;
        this.touch();
    }

    // Dates are advisory rather than enforced: §1 says no fixed duration, so a running sprint
    // can be rescheduled without ending it.
    public void reschedule(LocalDate newStart, LocalDate newEnd) {
        LocalDate start = newStart == null ? this.startDate : newStart;
        LocalDate end = newEnd == null ? this.endDate : newEnd;
        requireOrderedDates(start, end);
        this.startDate = start;
        this.endDate = end;
        this.touch();
    }

    public void start() {
        if (status != SprintStatus.PLANNED) {
            throw new InvalidSprintTransitionException(
                    "Only a PLANNED sprint can be started; this one is " + status);
        }
        this.status = SprintStatus.ACTIVE;
        this.touch();
    }

    public void close() {
        if (status != SprintStatus.ACTIVE) {
            throw new InvalidSprintTransitionException(
                    "Only an ACTIVE sprint can be closed; this one is " + status);
        }
        this.status = SprintStatus.COMPLETED;
        this.touch();
    }

    public boolean isActive() {
        return status == SprintStatus.ACTIVE;
    }

    public boolean isCompleted() {
        return status == SprintStatus.COMPLETED;
    }

    private static void requireOrderedDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new InvalidSprintDatesException();
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
