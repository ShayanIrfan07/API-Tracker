package com.apitracker.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.alert.service.AlertService;
import com.apitracker.monitor.checker.HttpCheckClient;
import com.apitracker.monitor.checker.HttpCheckOutcome;
import com.apitracker.monitor.dto.MonitoredApiResponse;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.CheckResult;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.evaluation.StatusEvaluator;
import com.apitracker.monitor.evaluation.StatusEvaluator.EvaluationResult;
import com.apitracker.monitor.metrics.MonitoringMetrics;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @Mock
    private MonitoredApiRepository monitoredApiRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private HttpCheckClient httpCheckClient;

    @Mock
    private StatusEvaluator statusEvaluator;

    @Mock
    private MonitoredApiService monitoredApiService;

    @Mock
    private AlertService alertService;

    @Mock
    private MonitoringMetrics monitoringMetrics;

    @InjectMocks
    private HealthCheckService healthCheckService;

    private UUID apiId;
    private MonitoredApi api;

    @BeforeEach
    void setUp() {
        apiId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        api = MonitoredApi.builder()
                .id(apiId)
                .name("Payments API")
                .baseUrl("https://api.example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(1000)
                .intervalSeconds(30)
                .failureThreshold(3)
                .successThreshold(2)
                .enabled(true)
                .currentStatus(ApiStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void checkNowPersistsResultEvaluatesAndAlertsOnDown() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(httpCheckClient.check(api)).thenReturn(HttpCheckOutcome.failed(500, 42, "error"));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(statusEvaluator.apply(
                        any(MonitoredApi.class),
                        org.mockito.ArgumentMatchers.eq(false),
                        org.mockito.ArgumentMatchers.eq(42),
                        any(Instant.class)))
                .thenAnswer(invocation -> {
                    api.setCurrentStatus(ApiStatus.DOWN);
                    return new EvaluationResult(ApiStatus.UNKNOWN, ApiStatus.DOWN, true);
                });
        when(monitoredApiRepository.save(api)).thenReturn(api);
        when(monitoredApiService.toResponse(api)).thenReturn(sampleResponse());

        MonitoredApiResponse response = healthCheckService.checkNow(apiId);

        verify(alertService).handleTransitionToDown(api);
        verify(monitoringMetrics).recordCheck(false, 42);
        assertThat(response.id()).isEqualTo(apiId);
    }

    @Test
    void checkNowRejectsWhenAlreadyInFlight() {
        assertThat(healthCheckService.tryBegin(apiId)).isTrue();

        assertThatThrownBy(() -> healthCheckService.checkNow(apiId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in progress");

        healthCheckService.end(apiId);
    }

    private MonitoredApiResponse sampleResponse() {
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        return new MonitoredApiResponse(
                apiId,
                "Payments API",
                "https://api.example.com",
                "/health",
                HttpMethod.GET,
                200,
                1000,
                30,
                3,
                2,
                null,
                "ops@example.com",
                true,
                ApiStatus.DOWN,
                3,
                0,
                now,
                now,
                now,
                now);
    }
}
