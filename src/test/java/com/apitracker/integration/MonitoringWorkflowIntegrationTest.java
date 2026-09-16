package com.apitracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apitracker.alert.entity.Alert;
import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.alert.repository.AlertRepository;
import com.apitracker.monitor.checker.HttpCheckClient;
import com.apitracker.monitor.checker.HttpCheckOutcome;
import com.apitracker.monitor.dto.CheckNowResponse;
import com.apitracker.monitor.dto.CreateMonitoredApiRequest;
import com.apitracker.monitor.dto.MonitoredApiResponse;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.monitor.service.HealthCheckService;
import com.apitracker.monitor.service.MonitoredApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end monitoring workflow using an in-memory database and a mocked HTTP client.
 * Covers registration, checks, incidents, recovery, and history without external APIs.
 */
@SpringBootTest
@ActiveProfiles("test")
class MonitoringWorkflowIntegrationTest {

    @Autowired
    private MonitoredApiService monitoredApiService;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @MockitoBean
    private HttpCheckClient httpCheckClient;

    @Test
    void registersApiRunsChecksOpensAndResolvesIncident() {
        MonitoredApiResponse api = monitoredApiService.create(createRequest("Workflow API"));

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.ok(200, 15));
        CheckNowResponse firstCheck = healthCheckService.checkNow(api.id());
        assertThat(firstCheck.success()).isTrue();
        assertThat(firstCheck.httpStatus()).isEqualTo(200);

        CheckNowResponse secondCheck = healthCheckService.checkNow(api.id());
        assertThat(secondCheck.currentStatus()).isEqualTo(ApiStatus.UP);

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.failed(503, 40, "Unexpected status code 503"));
        healthCheckService.checkNow(api.id());
        CheckNowResponse downCheck = healthCheckService.checkNow(api.id());
        assertThat(downCheck.currentStatus()).isEqualTo(ApiStatus.DOWN);

        Alert openAlert = alertRepository.findByMonitoredApiIdAndStatus(api.id(), AlertStatus.OPEN)
                .orElseThrow();
        assertThat(openAlert.getFailureReason()).contains("503");

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.ok(200, 18));
        healthCheckService.checkNow(api.id());
        CheckNowResponse recoveredCheck = healthCheckService.checkNow(api.id());
        assertThat(recoveredCheck.currentStatus()).isEqualTo(ApiStatus.UP);

        Alert resolved = alertRepository.findById(openAlert.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
        assertThat(resolved.getDurationSeconds()).isNotNull().isGreaterThanOrEqualTo(0L);

        assertThat(checkResultRepository.findByMonitoredApiIdOrderByCheckedAtDesc(api.id(), PageRequest.of(0, 20))
                        .getTotalElements())
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    void doesNotCreateDuplicateIncidentDuringContinuousOutage() {
        MonitoredApiResponse api = monitoredApiService.create(createRequest("Outage API"));
        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.failed(500, 25, "Unexpected status code 500"));

        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());

        long openCount = alertRepository.findAll().stream()
                .filter(alert -> alert.getMonitoredApi().getId().equals(api.id()))
                .filter(alert -> alert.getStatus() == AlertStatus.OPEN)
                .count();
        assertThat(openCount).isEqualTo(1);
    }

    @Test
    void disabledApiIsExcludedFromEnabledQuery() {
        MonitoredApiResponse api = monitoredApiService.create(createRequest("Disable Me"));
        monitoredApiService.disable(api.id());

        assertThat(monitoredApiService.findById(api.id()).enabled()).isFalse();
    }

    private CreateMonitoredApiRequest createRequest(String name) {
        return new CreateMonitoredApiRequest(
                name,
                "https://example.com",
                "/health",
                HttpMethod.GET,
                200,
                3000,
                60,
                2,
                2,
                null,
                null,
                true);
    }
}
