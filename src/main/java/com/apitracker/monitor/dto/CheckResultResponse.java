package com.apitracker.monitor.dto;

import java.time.Instant;
import java.util.UUID;

public record CheckResultResponse(
        Long id,
        UUID apiId,
        Instant checkedAt,
        Boolean success,
        Integer httpStatus,
        Integer latencyMs,
        String errorMessage
) {
}
