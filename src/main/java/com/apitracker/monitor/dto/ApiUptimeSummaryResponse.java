package com.apitracker.monitor.dto;

import com.apitracker.monitor.entity.ApiStatus;
import java.time.Instant;
import java.util.UUID;

public record ApiUptimeSummaryResponse(
        UUID apiId,
        String name,
        ApiStatus currentStatus,
        Boolean enabled,
        int windowHours,
        Instant windowStart,
        Instant windowEnd,
        long totalChecks,
        long successfulChecks,
        long failedChecks,
        Double uptimePercent,
        Double avgLatencyMs,
        Integer minLatencyMs,
        Integer maxLatencyMs
) {
}
