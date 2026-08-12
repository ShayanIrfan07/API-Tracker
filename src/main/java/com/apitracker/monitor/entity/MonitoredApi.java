package com.apitracker.monitor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "monitored_api")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoredApi {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @NotBlank
    @Size(max = 500)
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String path;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false, length = 16)
    private HttpMethod httpMethod;

    @NotNull
    @Min(100)
    @Max(599)
    @Column(name = "expected_status_code", nullable = false)
    private Integer expectedStatusCode;

    @NotNull
    @Positive
    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;

    @NotNull
    @Positive
    @Column(name = "interval_seconds", nullable = false)
    private Integer intervalSeconds;

    @NotNull
    @Positive
    @Column(name = "failure_threshold", nullable = false)
    @Builder.Default
    private Integer failureThreshold = 3;

    @NotNull
    @Positive
    @Column(name = "success_threshold", nullable = false)
    @Builder.Default
    private Integer successThreshold = 2;

    @NotNull
    @Column(nullable = false)
    private Boolean enabled;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 16)
    private ApiStatus currentStatus;

    @NotNull
    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;

    @NotNull
    @Column(name = "consecutive_successes", nullable = false)
    @Builder.Default
    private Integer consecutiveSuccesses = 0;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_status_change_at")
    private Instant lastStatusChangeAt;

    @Size(max = 255)
    @Column(name = "owner_email", length = 255)
    private String ownerEmail;

    @Positive
    @Column(name = "latency_threshold_ms")
    private Integer latencyThresholdMs;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (path == null || path.isBlank()) {
            path = "/";
        }
        if (enabled == null) {
            enabled = true;
        }
        if (currentStatus == null) {
            currentStatus = ApiStatus.UNKNOWN;
        }
        if (failureThreshold == null) {
            failureThreshold = 3;
        }
        if (successThreshold == null) {
            successThreshold = 2;
        }
        if (consecutiveFailures == null) {
            consecutiveFailures = 0;
        }
        if (consecutiveSuccesses == null) {
            consecutiveSuccesses = 0;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
