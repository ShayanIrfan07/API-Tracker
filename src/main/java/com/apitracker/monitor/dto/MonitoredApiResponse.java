package com.apitracker.monitor.dto;

import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import java.time.Instant;
import java.util.UUID;

public record MonitoredApiResponse(
        UUID id,
        String name,
        String baseUrl,
        String path,
        HttpMethod httpMethod,
        Integer expectedStatusCode,
        Integer timeoutMs,
        Integer intervalSeconds,
        Integer failureThreshold,
        Integer successThreshold,
        Integer latencyThresholdMs,
        String ownerEmail,
        Boolean enabled,
        ApiStatus currentStatus,
        Integer consecutiveFailures,
        Integer consecutiveSuccesses,
        Instant lastCheckedAt,
        Instant lastStatusChangeAt,
        Instant createdAt,
        Instant updatedAt
) {
}
