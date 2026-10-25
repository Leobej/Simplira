package io.github.leobej.application.api.chart;

import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.chart.BurndownChart;
import io.github.leobej.domain.model.chart.CumulativeFlowChart;
import io.github.leobej.domain.model.chart.VelocityChart;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.ChartService;
import io.github.leobej.infrastructure.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

// The chart records are read models built for exactly this output, so they go out as they are
// rather than being copied field for field into response twins.
@RestController
@RequestMapping("/api/v1")
public class ChartController {

    private static final int DEFAULT_VELOCITY_SPRINTS = 10;
    private static final int MAX_VELOCITY_SPRINTS = 50;

    private final ChartService chartService;

    public ChartController(ChartService chartService) {
        this.chartService = chartService;
    }

    @GetMapping("/sprints/{sprintId}/charts/burndown")
    public ApiResponse<BurndownChart> burndown(@PathVariable UUID sprintId, @CurrentUser User user) {
        return ApiResponse.success(chartService.burndown(sprintId, user.getId()));
    }

    @GetMapping("/projects/{projectId}/charts/velocity")
    public ApiResponse<VelocityChart> velocity(@PathVariable UUID projectId,
                                               @RequestParam(required = false) Integer sprints,
                                               @CurrentUser User user) {
        int limit = sprints == null || sprints < 1 ? DEFAULT_VELOCITY_SPRINTS : Math.min(sprints, MAX_VELOCITY_SPRINTS);
        return ApiResponse.success(chartService.velocity(projectId, limit, user.getId()));
    }

    @GetMapping("/projects/{projectId}/charts/cumulative-flow")
    public ApiResponse<CumulativeFlowChart> cumulativeFlow(
            @PathVariable UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @CurrentUser User user) {
        return ApiResponse.success(chartService.cumulativeFlow(projectId, from, to, user.getId()));
    }
}
