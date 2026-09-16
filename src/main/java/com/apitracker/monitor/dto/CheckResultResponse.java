package com.apitracker.monitor.dto;

import com.apitracker.monitor.entity.ApiStatus;
import java.time.Instant;
import java.util.UUID;

public record CheckResultResponse(
        Long id,
        UUID apiId,
        Instant checkedAt,
        Boolean success,
        ApiStatus apiStatus,
        Integer httpStatus,
        Integer latencyMs,
        Boolean timedOut,
        String errorMessage
) {
}
