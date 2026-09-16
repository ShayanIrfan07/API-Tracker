package com.apitracker.monitor.dto;

import com.apitracker.monitor.entity.ApiStatus;
import java.time.Instant;
import java.util.UUID;

public record CheckNowResponse(
        UUID apiId,
        String apiName,
        ApiStatus currentStatus,
        Boolean success,
        Integer httpStatus,
        Integer latencyMs,
        Boolean timedOut,
        String errorMessage,
        Instant checkedAt,
        Long checkResultId
) {
}
