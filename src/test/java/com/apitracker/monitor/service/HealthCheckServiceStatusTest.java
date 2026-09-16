package com.apitracker.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.alert.service.AlertService;
import com.apitracker.monitor.checker.HttpCheckClient;
import com.apitracker.monitor.checker.HttpCheckOutcome;
import com.apitracker.monitor.dto.CheckNowResponse;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.CheckResult;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.evaluation.StatusEvaluator;
import com.apitracker.monitor.metrics.MonitoringMetrics;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceStatusTest {

    @Mock
    private MonitoredApiRepository monitoredApiRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private HttpCheckClient httpCheckClient;

    @Mock
    private AlertService alertService;

    @Mock
    private MonitoringMetrics monitoringMetrics;

    private HealthCheckService healthCheckService;
    private UUID apiId;
    private MonitoredApi api;

    @BeforeEach
    void setUp() {
        healthCheckService = new HealthCheckService(
                monitoredApiRepository,
                checkResultRepository,
                httpCheckClient,
                new StatusEvaluator(),
                alertService,
                monitoringMetrics);

        apiId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        api = MonitoredApi.builder()
                .id(apiId)
                .name("Status API")
                .baseUrl("https://example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(1000)
                .intervalSeconds(30)
                .failureThreshold(2)
                .successThreshold(2)
                .latencyThresholdMs(100)
                .enabled(true)
                .currentStatus(ApiStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void consecutiveFailuresMarkApiDownThroughRealEvaluator() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(httpCheckClient.check(api)).thenReturn(HttpCheckOutcome.failed(null, 50, "Connection refused"));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(monitoredApiRepository.save(any(MonitoredApi.class))).thenAnswer(invocation -> invocation.getArgument(0));

        healthCheckService.checkNow(apiId);
        healthCheckService.checkNow(apiId);

        assertThat(api.getCurrentStatus()).isEqualTo(ApiStatus.DOWN);
        verify(alertService).handleTransitionToDown(api);
    }

    @Test
    void persistsEvaluatedStatusOnCheckResult() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(httpCheckClient.check(api)).thenReturn(HttpCheckOutcome.ok(200, 250));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(monitoredApiRepository.save(any(MonitoredApi.class))).thenAnswer(invocation -> invocation.getArgument(0));

        healthCheckService.checkNow(apiId);
        healthCheckService.checkNow(apiId);

        ArgumentCaptor<CheckResult> captor = ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        CheckResult lastSaved = captor.getAllValues().get(1);

        assertThat(lastSaved.getApiStatus()).isEqualTo(ApiStatus.DEGRADED);
        assertThat(lastSaved.getSuccess()).isTrue();
        assertThat(lastSaved.getHttpStatus()).isEqualTo(200);
        assertThat(lastSaved.getTimedOut()).isFalse();
        assertThat(lastSaved.getLatencyMs()).isEqualTo(250);
    }

    @Test
    void slowSuccessfulChecksMarkApiDegradedThroughRealEvaluator() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(httpCheckClient.check(api)).thenReturn(HttpCheckOutcome.ok(200, 250));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(monitoredApiRepository.save(any(MonitoredApi.class))).thenAnswer(invocation -> invocation.getArgument(0));

        healthCheckService.checkNow(apiId);
        CheckNowResponse response = healthCheckService.checkNow(apiId);

        assertThat(api.getCurrentStatus()).isEqualTo(ApiStatus.DEGRADED);
        assertThat(response.currentStatus()).isEqualTo(ApiStatus.DEGRADED);
        assertThat(response.success()).isTrue();
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.latencyMs()).isEqualTo(250);
    }
}
