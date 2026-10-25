package io.github.leobej.domain.model.chart;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Issues, not points: the schema has no estimates. total is the sprint's scope - its issues plus
// any carried out of it when it closed - and each day says how many were still open at its end.
public record BurndownChart(UUID sprintId,
                            String sprintName,
                            LocalDate startDate,
                            LocalDate endDate,
                            int total,
                            List<Day> days) {

    // remaining is null for days that have not happened yet, so a client draws the ideal line
    // across the whole sprint and the actual one only as far as today.
    public record Day(LocalDate date, Integer remaining, int completed, double ideal) {}
}
