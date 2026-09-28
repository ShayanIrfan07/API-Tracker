package com.apitracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.alert.repository.AlertRepository;
import com.apitracker.monitor.checker.HttpCheckClient;
import com.apitracker.monitor.checker.HttpCheckOutcome;
import com.apitracker.monitor.dto.CreateMonitoredApiRequest;
import com.apitracker.monitor.dto.MonitoredApiResponse;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.service.HealthCheckService;
import com.apitracker.monitor.service.MonitoredApiService;
import com.apitracker.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class MissingOwnerEmailWorkflowIntegrationTest {

    @Autowired
    private MonitoredApiService monitoredApiService;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @MockitoBean
    private HttpCheckClient httpCheckClient;

    @Test
    void opensIncidentAndSkipsEmailWhenOwnerEmailMissing() {
        MonitoredApiResponse api = monitoredApiService.create(new CreateMonitoredApiRequest(
                "No Owner API",
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
                true));

        when(httpCheckClient.check(any())).thenReturn(HttpCheckOutcome.failed(500, 20, "error"));
        healthCheckService.checkNow(api.id());
        healthCheckService.checkNow(api.id());

        assertThat(alertRepository.findByMonitoredApiIdAndStatus(api.id(), AlertStatus.OPEN)).isPresent();
        assertThat(notificationLogRepository.findAll()).anyMatch(log -> Boolean.FALSE.equals(log.getSuccess())
                && log.getErrorMessage() != null
                && log.getErrorMessage().contains("ownerEmail"));
    }
}
