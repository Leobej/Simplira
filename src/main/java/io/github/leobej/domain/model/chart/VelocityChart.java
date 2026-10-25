package io.github.leobej.domain.model.chart;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Completed sprints oldest first. committed is what the sprint held when it closed - what was
// finished plus what was carried out of it - so the gap between the two is the overcommitment.
public record VelocityChart(List<Sprint> sprints, double averageCompleted) {

    public record Sprint(UUID sprintId, String name, LocalDate startDate, LocalDate endDate,
                         int committed, int completed) {}
}
