package com.apitracker.alert.dto;

import com.apitracker.alert.entity.AlertStatus;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID apiId,
        String apiName,
        AlertStatus status,
        Instant openedAt,
        Instant resolvedAt,
        String jiraIssueKey,
        String jiraIssueUrl,
        String summary,
        String detail,
        String failureReason,
        Long durationSeconds
) {
}
