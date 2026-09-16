package com.apitracker.monitor.dto;

import java.time.Instant;
import java.util.List;

public record FleetSummaryResponse(
        int windowHours,
        Instant windowStart,
        Instant windowEnd,
        int totalApis,
        int enabledApis,
        int upCount,
        int degradedCount,
        int downCount,
        int unknownCount,
        long totalChecks,
        long successfulChecks,
        Double fleetUptimePercent,
        Double fleetAvgLatencyMs,
        long openIncidentCount,
        long resolvedIncidentCount,
        Double mttrSeconds,
        List<ApiUptimeSummaryResponse> apis
) {
}
