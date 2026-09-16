package com.apitracker.monitor.dto;

import com.apitracker.monitor.entity.HttpMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMonitoredApiRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotBlank(message = "baseUrl is required")
        @Size(max = 500, message = "baseUrl must be at most 500 characters")
        String baseUrl,

        @Size(max = 500, message = "path must be at most 500 characters")
        String path,

        @NotNull(message = "httpMethod is required")
        HttpMethod httpMethod,

        @NotNull(message = "expectedStatusCode is required")
        @Min(value = 100, message = "expectedStatusCode must be between 100 and 599")
        @Max(value = 599, message = "expectedStatusCode must be between 100 and 599")
        Integer expectedStatusCode,

        @NotNull(message = "timeoutMs is required")
        @Min(value = 100, message = "timeoutMs must be at least 100")
        @Max(value = 120_000, message = "timeoutMs must be at most 120000")
        Integer timeoutMs,

        @NotNull(message = "intervalSeconds is required")
        @Min(value = 10, message = "intervalSeconds must be at least 10")
        @Max(value = 86_400, message = "intervalSeconds must be at most 86400")
        Integer intervalSeconds,

        @Positive(message = "failureThreshold must be positive")
        Integer failureThreshold,

        @Positive(message = "successThreshold must be positive")
        Integer successThreshold,

        @Positive(message = "latencyThresholdMs must be positive")
        Integer latencyThresholdMs,

        @Email(message = "ownerEmail must be a valid email")
        @Size(max = 255, message = "ownerEmail must be at most 255 characters")
        String ownerEmail,

        Boolean enabled
) {
}
