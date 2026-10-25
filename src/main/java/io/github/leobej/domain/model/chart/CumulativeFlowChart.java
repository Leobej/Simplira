package io.github.leobej.domain.model.chart;

import java.time.LocalDate;
import java.util.List;

// How many of the project's issues were in each status category at the end of each day. By
// category, never by status name (§33), so a renamed or added column does not break the bands.
public record CumulativeFlowChart(LocalDate from, LocalDate to, List<Day> days) {

    public record Day(LocalDate date, int todo, int inProgress, int done) {}
}
