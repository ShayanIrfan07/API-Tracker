package com.apitracker.monitor.repository;

import java.util.UUID;

public record ApiCheckAggregate(
        UUID apiId,
        long totalChecks,
        long successfulChecks,
        Double avgLatencyMs,
        Integer maxLatencyMs,
        Integer minLatencyMs
) {
}
