package com.apitracker.monitor.repository;

public record CheckAggregate(
        long totalChecks,
        long successfulChecks,
        Double avgLatencyMs,
        Integer maxLatencyMs,
        Integer minLatencyMs
) {
}
