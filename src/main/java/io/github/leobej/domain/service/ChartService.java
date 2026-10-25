package io.github.leobej.domain.service;

import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.audit.AuditEntry;
import io.github.leobej.domain.model.chart.BurndownChart;
import io.github.leobej.domain.model.chart.CumulativeFlowChart;
import io.github.leobej.domain.model.chart.VelocityChart;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueStatusCategory;
import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.domain.repository.AuditLogRepository;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.IssueStatusRepository;
import io.github.leobej.domain.repository.SprintRepository;
import io.github.leobej.shared.exception.InvalidDateRangeException;
import io.github.leobej.shared.exception.ProjectNotFoundException;
import io.github.leobej.shared.exception.SprintNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// The three charts in §7, aggregated here rather than in the client (§7). Counts are issues, not
// points - there are no estimates - and "done" is the DONE status category, never a status name
// (§33).
//
// History comes from the audit log, which already records every status change and sprint move
// with its time, so nothing extra is written on the way in. The cost: charts only know what
// happened since the log existed (V25); earlier changes show as the issue's current state.
// Days are UTC days.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class ChartService {

    public static final int DEFAULT_FLOW_DAYS = 30;
    public static final int MAX_FLOW_DAYS = 180;

    private final IssueRepository issueRepository;
    private final SprintRepository sprintRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final AuditLogRepository auditLogRepository;
    private final ProjectAuthorization projectAuthorization;
    private final Clock clock;

    public ChartService(IssueRepository issueRepository,
                        SprintRepository sprintRepository,
                        IssueStatusRepository issueStatusRepository,
                        AuditLogRepository auditLogRepository,
                        ProjectAuthorization projectAuthorization,
                        Clock clock) {
        this.issueRepository = issueRepository;
        this.sprintRepository = sprintRepository;
        this.issueStatusRepository = issueStatusRepository;
        this.auditLogRepository = auditLogRepository;
        this.projectAuthorization = projectAuthorization;
        this.clock = clock;
    }

    public BurndownChart burndown(UUID sprintId, UUID actorId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow(() -> new SprintNotFoundException(sprintId));
        try {
            projectAuthorization.requireAccess(sprint.getProjectId(), actorId);
        } catch (ProjectNotFoundException ex) {
            throw new SprintNotFoundException(sprintId);
        }

        // What it still holds, plus what its close carried out: those were part of the commitment.
        Map<UUID, Issue> scope = new LinkedHashMap<>();
        issueRepository.findBySprintId(sprintId).forEach(issue -> scope.put(issue.getId(), issue));
        issueRepository.findAllById(auditLogRepository.findIssuesCarriedOutOf(sprintId))
                .forEach(issue -> scope.put(issue.getId(), issue));

        LocalDate today = LocalDate.now(clock);
        int total = scope.size();
        long length = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;
        List<BurndownChart.Day> days = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            LocalDate day = sprint.getStartDate().plusDays(i);
            int completed = (int) scope.values().stream()
                    .filter(issue -> issue.getClosedAt() != null && !dayOf(issue.getClosedAt()).isAfter(day))
                    .count();
            Integer remaining = day.isAfter(today) ? null : total - completed;
            double ideal = length == 1 ? 0 : total * (1 - (double) i / (length - 1));
            days.add(new BurndownChart.Day(day, remaining, day.isAfter(today) ? 0 : completed, ideal));
        }
        return new BurndownChart(sprintId, sprint.getName(), sprint.getStartDate(), sprint.getEndDate(), total, days);
    }

    public VelocityChart velocity(UUID projectId, int limit, UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);

        List<Sprint> completed = sprintRepository.findByProjectId(projectId).stream()
                .filter(Sprint::isCompleted)
                .sorted(Comparator.comparing(Sprint::getEndDate).reversed())
                .limit(Math.max(1, limit))
                .sorted(Comparator.comparing(Sprint::getEndDate))
                .toList();

        // A sprint closed before the audit log existed has no record of its carry-over, and shows
        // committed equal to completed.
        Map<UUID, Integer> carriedOver = auditLogRepository.findSprintClosures(projectId).stream()
                .filter(entry -> entry.newValue() != null && entry.newValue().get("carriedOver") instanceof Number)
                .collect(Collectors.toMap(AuditEntry::entityId,
                        entry -> ((Number) entry.newValue().get("carriedOver")).intValue(),
                        (first, later) -> later));

        // Finished issues stay attached to the sprint they were finished in (§29), so they are
        // still there to count.
        List<VelocityChart.Sprint> rows = completed.stream().map(sprint -> {
            int done = (int) issueRepository.findBySprintId(sprint.getId()).stream()
                    .filter(issue -> issue.getClosedAt() != null)
                    .count();
            return new VelocityChart.Sprint(sprint.getId(), sprint.getName(), sprint.getStartDate(),
                    sprint.getEndDate(), done + carriedOver.getOrDefault(sprint.getId(), 0), done);
        }).toList();

        double average = rows.stream().mapToInt(VelocityChart.Sprint::completed).average().orElse(0);
        return new VelocityChart(rows, average);
    }

    // from and to are inclusive; both default to the last 30 days.
    public CumulativeFlowChart cumulativeFlow(UUID projectId, LocalDate from, LocalDate to, UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);

        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_FLOW_DAYS - 1) : from;
        if (start.isAfter(end)) {
            throw new InvalidDateRangeException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_FLOW_DAYS) {
            throw new InvalidDateRangeException("A cumulative flow covers at most " + MAX_FLOW_DAYS + " days");
        }

        Map<UUID, IssueStatusCategory> categoryOfStatus = issueStatusRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(IssueStatus::getId, IssueStatus::getCategory));
        Map<UUID, List<AuditEntry>> historyByIssue = auditLogRepository.findIssueStatusHistory(projectId).stream()
                .collect(Collectors.groupingBy(AuditEntry::issueId));

        List<Timeline> timelines = issueRepository.findAllInProject(projectId).stream()
                .map(issue -> Timeline.of(issue, historyByIssue.getOrDefault(issue.getId(), List.of()), categoryOfStatus))
                .toList();

        List<CumulativeFlowChart.Day> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            Instant endOfDay = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Map<IssueStatusCategory, Long> counts = timelines.stream()
                    .filter(timeline -> timeline.createdAt().isBefore(endOfDay))
                    .collect(Collectors.groupingBy(timeline -> timeline.categoryBefore(endOfDay), Collectors.counting()));
            days.add(new CumulativeFlowChart.Day(day,
                    counts.getOrDefault(IssueStatusCategory.TODO, 0L).intValue(),
                    counts.getOrDefault(IssueStatusCategory.IN_PROGRESS, 0L).intValue(),
                    counts.getOrDefault(IssueStatusCategory.DONE, 0L).intValue()));
        }
        return new CumulativeFlowChart(start, end, days);
    }

    private static LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    // One issue's category over time, replayed from its audit entries.
    private record Timeline(Instant createdAt, IssueStatusCategory initial, List<Change> changes) {

        private record Change(Instant at, IssueStatusCategory category) {}

        static Timeline of(Issue issue, List<AuditEntry> entries, Map<UUID, IssueStatusCategory> categoryOfStatus) {
            IssueStatusCategory current = categoryOfStatus.getOrDefault(issue.getStatusId(), IssueStatusCategory.TODO);
            List<Change> changes = new ArrayList<>();
            IssueStatusCategory initial = null;
            for (AuditEntry entry : entries) {
                if (entry.action() == AuditAction.CREATED) {
                    initial = categoryOf(entry.newValue(), "statusId", categoryOfStatus);
                } else {
                    // STATUS_CHANGED and ISSUE_CLOSED carry the category itself (§33), so a status
                    // deleted since still reads correctly.
                    IssueStatusCategory category = category(entry.newValue());
                    if (initial == null) {
                        initial = category(entry.oldValue());
                    }
                    if (category != null) {
                        changes.add(new Change(entry.createdAt(), category));
                    }
                }
            }
            // No history at all - an issue from before the audit log - shows as it is now.
            return new Timeline(issue.getCreatedAt(), initial == null ? current : initial, changes);
        }

        IssueStatusCategory categoryBefore(Instant instant) {
            IssueStatusCategory category = initial;
            for (Change change : changes) {
                if (change.at().isBefore(instant)) {
                    category = change.category();
                }
            }
            return category;
        }

        private static IssueStatusCategory category(Map<String, Object> value) {
            if (value == null || !(value.get("category") instanceof String name)) {
                return null;
            }
            return IssueStatusCategory.valueOf(name);
        }

        private static IssueStatusCategory categoryOf(Map<String, Object> value, String key,
                                                      Map<UUID, IssueStatusCategory> categoryOfStatus) {
            if (value == null || !(value.get(key) instanceof String id)) {
                return null;
            }
            return categoryOfStatus.get(UUID.fromString(id));
        }
    }
}
