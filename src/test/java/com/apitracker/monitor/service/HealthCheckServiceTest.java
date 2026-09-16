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
import com.apitracker.monitor.dto.CheckNowResponse;
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
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
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

        CheckNowResponse response = healthCheckService.checkNow(apiId);

        verify(alertService).handleTransitionToDown(api);
        verify(monitoringMetrics).recordCheck(false, 42);
        assertThat(response.apiId()).isEqualTo(apiId);
        assertThat(response.apiName()).isEqualTo("Payments API");
        assertThat(response.currentStatus()).isEqualTo(ApiStatus.DOWN);
        assertThat(response.success()).isFalse();
        assertThat(response.httpStatus()).isEqualTo(500);
        assertThat(response.latencyMs()).isEqualTo(42);
        assertThat(response.errorMessage()).isEqualTo("error");
        assertThat(response.checkedAt()).isNotNull();
        assertThat(response.checkResultId()).isEqualTo(42L);
    }

    @Test
    void checkNowReturnsCheckOutcomeFieldsOnSuccess() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(httpCheckClient.check(api)).thenReturn(HttpCheckOutcome.ok(200, 18));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(invocation -> {
            CheckResult saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(statusEvaluator.apply(
                        any(MonitoredApi.class),
                        org.mockito.ArgumentMatchers.eq(true),
                        org.mockito.ArgumentMatchers.eq(18),
                        any(Instant.class)))
                .thenAnswer(invocation -> {
                    api.setCurrentStatus(ApiStatus.UP);
                    return new EvaluationResult(ApiStatus.UNKNOWN, ApiStatus.UP, true);
                });
        when(monitoredApiRepository.save(api)).thenReturn(api);

        CheckNowResponse response = healthCheckService.checkNow(apiId);

        assertThat(response.success()).isTrue();
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.latencyMs()).isEqualTo(18);
        assertThat(response.timedOut()).isFalse();
        assertThat(response.errorMessage()).isNull();
        assertThat(response.currentStatus()).isEqualTo(ApiStatus.UP);
    }

    @Test
    void checkNowRejectsWhenAlreadyInFlight() {
        assertThat(healthCheckService.tryBegin(apiId)).isTrue();

        assertThatThrownBy(() -> healthCheckService.checkNow(apiId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in progress");

        healthCheckService.end(apiId);
    }

}
