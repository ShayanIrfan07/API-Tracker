package com.apitracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.alert.entity.Alert;
import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.alert.repository.AlertRepository;
import com.apitracker.monitor.checker.HttpCheckClient;
import com.apitracker.monitor.checker.HttpCheckOutcome;
import com.apitracker.monitor.dto.CreateMonitoredApiRequest;
import com.apitracker.monitor.dto.MonitoredApiResponse;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.monitor.service.HealthCheckService;
import com.apitracker.monitor.service.MonitoredApiService;
import com.apitracker.notification.service.EmailNotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Automated monitoring workflow: scheduler-driven checks are exercised via {@link HealthCheckService}
 * (same code path as {@link com.apitracker.monitor.scheduler.ScheduledCheckRunner}).
 */
@SpringBootTest
@ActiveProfiles("test")
class CompleteMonitoringWorkflowIntegrationTest {

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

    @MockitoBean
    private EmailNotificationService emailNotificationService;

    @Test
    void healthyApiStaysUpWithoutIncidentOrEmail() {
        MonitoredApiResponse api = monitoredApiService.create(request("Healthy API", "owner@example.com", 2, 2));

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.ok(200, 50));
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());

        assertThat(monitoredApiService.findById(api.id()).currentStatus()).isEqualTo(ApiStatus.UP);
        assertThat(alertRepository.findByMonitoredApiIdAndStatus(api.id(), AlertStatus.OPEN)).isEmpty();
        verify(emailNotificationService, never()).sendDown(any(), any());
        verify(emailNotificationService, never()).sendRecovered(any(), any());
    }

    @Test
    void failureThresholdOpensIncidentAndSendsOneAlertEmail() {
        MonitoredApiResponse api = monitoredApiService.create(request("Payment API", "owner@example.com", 2, 2));

        when(httpCheckClient.check(any())).thenReturn(
                HttpCheckOutcome.failed(500, 850, "Unexpected status code: expected 200 but got 500"));
        healthCheckService.runScheduledCheck(api.id());
        healthCheckService.runScheduledCheck(api.id());

        Alert open = alertRepository.findByMonitoredApiIdAndStatus(api.id(), AlertStatus.OPEN).orElseThrow();
        assertThat(open.getFailureReason()).contains("500");
        verify(emailNotificationService, times(1)).sendDown(any(), any());
    }

    @Test
    void sustainedOutageKeepsSingleOpenIncidentAndNoDuplicateEmails() {
        MonitoredApiResponse api = monitoredApiService.create(request("Outage API", "owner@example.com", 2, 2));
        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.failed(500, 40, "Unexpected status code 500"));

        for (int i = 0; i < 5; i++) {
            healthCheckService.runScheduledCheck(api.id());
        }

        long openIncidents = alertRepository.findAll().stream()
                .filter(a -> a.getMonitoredApi().getId().equals(api.id()))
                .filter(a -> a.getStatus() == AlertStatus.OPEN)
                .count();
        assertThat(openIncidents).isEqualTo(1);
        verify(emailNotificationService, times(1)).sendDown(any(), any());
    }

    @Test
    void recoveryResolvesIncidentAndSendsRecoveryEmail() {
        MonitoredApiResponse api = monitoredApiService.create(request("Recover API", "owner@example.com", 2, 2));
        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.failed(503, 30, "Unexpected status code 503"));
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());
        Alert open = alertRepository.findByMonitoredApiIdAndStatus(api.id(), AlertStatus.OPEN).orElseThrow();

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.ok(200, 20));
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());

        Alert resolved = alertRepository.findById(open.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        verify(emailNotificationService, times(1)).sendRecovered(any(), any());
    }

    @Test
    void newFailureAfterRecoveryCreatesNewIncidentAndAlert() {
        MonitoredApiResponse api = monitoredApiService.create(request("Flappy API", "owner@example.com", 2, 2));

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.failed(500, 25, "down"));
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());
        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.ok(200, 15));
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.failed(500, 25, "down again"));
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());

        List<Alert> incidents = alertRepository.findAll().stream()
                .filter(a -> a.getMonitoredApi().getId().equals(api.id()))
                .toList();
        assertThat(incidents).hasSize(2);
        assertThat(incidents.stream().filter(a -> a.getStatus() == AlertStatus.RESOLVED).count()).isEqualTo(1);
        assertThat(incidents.stream().filter(a -> a.getStatus() == AlertStatus.OPEN).count()).isEqualTo(1);
        verify(emailNotificationService, times(2)).sendDown(any(), any());
    }

    @Test
    void timeoutFailureFollowsIncidentPathAndSchedulerContinues() {
        MonitoredApiResponse api = monitoredApiService.create(request("Timeout API", "owner@example.com", 2, 2));
        when(httpCheckClient.check(any()))
                .thenReturn(HttpCheckOutcome.failed(null, 5000, "Connection timeout after 5000 ms", true));

        healthCheckService.runScheduledCheck(api.id());
        healthCheckService.runScheduledCheck(api.id());

        Alert open = alertRepository.findByMonitoredApiIdAndStatus(api.id(), AlertStatus.OPEN).orElseThrow();
        assertThat(open.getFailureReason()).contains("timed out");
        assertThat(checkResultRepository.findAll()).isNotEmpty();
        verify(emailNotificationService, atLeastOnce()).sendDown(any(), any());
    }

    /**
     * Simulates monitoring https://httpbin.org/status/500 (expect 200) then switching to a healthy endpoint.
     * Production does not depend on httpbin; the HTTP client is mocked.
     */
    @Test
    void simulatesHttpBin500Then200FailureAndRecoveryWorkflow() {
        MonitoredApiResponse api = monitoredApiService.create(new CreateMonitoredApiRequest(
                "HttpBin Payment API",
                "https://httpbin.org",
                "/status/500",
                HttpMethod.GET,
                200,
                5000,
                60,
                2,
                2,
                null,
                "owner@example.com",
                true));

        when(httpCheckClient.check(any())).thenReturn(
                HttpCheckOutcome.failed(500, 180, "Unexpected status code: expected 200 but got 500"));
        healthCheckService.runScheduledCheck(api.id());
        healthCheckService.runScheduledCheck(api.id());
        assertThat(alertRepository.findByMonitoredApiIdAndStatus(api.id(), AlertStatus.OPEN)).isPresent();

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.ok(200, 120));
        healthCheckService.runScheduledCheck(api.id());
        healthCheckService.runScheduledCheck(api.id());
        assertThat(monitoredApiService.findById(api.id()).currentStatus()).isEqualTo(ApiStatus.UP);
        assertThat(alertRepository.findByMonitoredApiIdAndStatus(api.id(), AlertStatus.OPEN)).isEmpty();
        verify(emailNotificationService, times(1)).sendDown(any(), any());
        verify(emailNotificationService, times(1)).sendRecovered(any(), any());
    }

    private CreateMonitoredApiRequest request(
            String name, String ownerEmail, int failureThreshold, int successThreshold) {
        return new CreateMonitoredApiRequest(
                name,
                "https://example.com",
                "/api/payment",
                HttpMethod.GET,
                200,
                5000,
                60,
                failureThreshold,
                successThreshold,
                null,
                ownerEmail,
                true);
    }
}
