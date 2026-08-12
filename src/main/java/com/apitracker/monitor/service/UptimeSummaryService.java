package com.apitracker.monitor.service;

import com.apitracker.exception.ResourceNotFoundException;
import com.apitracker.monitor.dto.ApiUptimeSummaryResponse;
import com.apitracker.monitor.dto.FleetSummaryResponse;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.repository.ApiCheckAggregate;
import com.apitracker.monitor.repository.CheckAggregate;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UptimeSummaryService {

    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 168;

    private final MonitoredApiRepository monitoredApiRepository;
    private final CheckResultRepository checkResultRepository;

    public ApiUptimeSummaryResponse summarizeApi(UUID apiId, int hours) {
        int windowHours = normalizeHours(hours);
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(windowHours, ChronoUnit.HOURS);

        MonitoredApi api = monitoredApiRepository.findById(apiId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored API not found: " + apiId));

        CheckAggregate aggregate = checkResultRepository.aggregateForApiSince(apiId, windowStart);
        return toApiSummary(api, windowHours, windowStart, windowEnd, aggregate);
    }

    public FleetSummaryResponse summarizeFleet(int hours) {
        int windowHours = normalizeHours(hours);
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(windowHours, ChronoUnit.HOURS);

        List<MonitoredApi> apis = monitoredApiRepository.findAll();
        Map<UUID, ApiCheckAggregate> aggregates = new HashMap<>();
        for (ApiCheckAggregate row : checkResultRepository.aggregateAllSince(windowStart)) {
            aggregates.put(row.apiId(), row);
        }

        List<ApiUptimeSummaryResponse> apiSummaries = new ArrayList<>(apis.size());
        long totalChecks = 0;
        long successfulChecks = 0;
        double latencyWeightedSum = 0;
        long latencySamples = 0;
        int up = 0;
        int degraded = 0;
        int down = 0;
        int unknown = 0;
        int enabled = 0;

        for (MonitoredApi api : apis) {
            if (Boolean.TRUE.equals(api.getEnabled())) {
                enabled++;
            }
            switch (api.getCurrentStatus() == null ? ApiStatus.UNKNOWN : api.getCurrentStatus()) {
                case UP -> up++;
                case DEGRADED -> degraded++;
                case DOWN -> down++;
                default -> unknown++;
            }

            ApiCheckAggregate row = aggregates.get(api.getId());
            CheckAggregate aggregate = row == null
                    ? null
                    : new CheckAggregate(
                            row.totalChecks(),
                            row.successfulChecks(),
                            row.avgLatencyMs(),
                            row.maxLatencyMs(),
                            row.minLatencyMs());
            ApiUptimeSummaryResponse summary = toApiSummary(api, windowHours, windowStart, windowEnd, aggregate);
            apiSummaries.add(summary);

            totalChecks += summary.totalChecks();
            successfulChecks += summary.successfulChecks();
            if (summary.avgLatencyMs() != null && summary.totalChecks() > 0) {
                latencyWeightedSum += summary.avgLatencyMs() * summary.totalChecks();
                latencySamples += summary.totalChecks();
            }
        }

        Double fleetUptime = totalChecks == 0
                ? null
                : round1((successfulChecks * 100.0) / totalChecks);
        Double fleetAvgLatency = latencySamples == 0
                ? null
                : round1(latencyWeightedSum / latencySamples);

        return new FleetSummaryResponse(
                windowHours,
                windowStart,
                windowEnd,
                apis.size(),
                enabled,
                up,
                degraded,
                down,
                unknown,
                totalChecks,
                successfulChecks,
                fleetUptime,
                fleetAvgLatency,
                apiSummaries
        );
    }

    private ApiUptimeSummaryResponse toApiSummary(
            MonitoredApi api,
            int windowHours,
            Instant windowStart,
            Instant windowEnd,
            CheckAggregate aggregate) {
        long total = 0;
        long successful = 0;
        Double avgLatency = null;
        Integer minLatency = null;
        Integer maxLatency = null;

        if (aggregate != null && aggregate.totalChecks() > 0) {
            total = aggregate.totalChecks();
            successful = aggregate.successfulChecks();
            if (aggregate.avgLatencyMs() != null) {
                avgLatency = round1(aggregate.avgLatencyMs());
            }
            maxLatency = aggregate.maxLatencyMs();
            minLatency = aggregate.minLatencyMs();
        }

        Double uptime = total == 0 ? null : round1((successful * 100.0) / total);

        return new ApiUptimeSummaryResponse(
                api.getId(),
                api.getName(),
                api.getCurrentStatus(),
                api.getEnabled(),
                windowHours,
                windowStart,
                windowEnd,
                total,
                successful,
                total - successful,
                uptime,
                avgLatency,
                minLatency,
                maxLatency
        );
    }

    private static int normalizeHours(int hours) {
        if (hours < MIN_HOURS) {
            return MIN_HOURS;
        }
        return Math.min(hours, MAX_HOURS);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
