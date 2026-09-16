package com.apitracker.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.alert.entity.Alert;
import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.alert.repository.AlertRepository;
import com.apitracker.config.JiraProperties;
import com.apitracker.integration.jira.JiraTicketService;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.CheckResult;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.metrics.MonitoringMetrics;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.notification.service.EmailNotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private JiraTicketService jiraTicketService;

    @Mock
    private JiraProperties jiraProperties;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private MonitoringMetrics monitoringMetrics;

    @InjectMocks
    private AlertService alertService;

    private MonitoredApi api;

    @BeforeEach
    void setUp() {
        api = MonitoredApi.builder()
                .id(UUID.randomUUID())
                .name("Payments API")
                .baseUrl("https://api.example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(1000)
                .intervalSeconds(30)
                .failureThreshold(2)
                .successThreshold(2)
                .ownerEmail("oncall@example.com")
                .enabled(true)
                .currentStatus(ApiStatus.DOWN)
                .consecutiveFailures(2)
                .consecutiveSuccesses(0)
                .build();
    }

    @Test
    void opensAlertCreatesJiraAndEmailsOnDown() {
        when(alertRepository.findByMonitoredApiIdAndStatus(api.getId(), AlertStatus.OPEN))
                .thenReturn(Optional.empty());
        when(checkResultRepository.findByMonitoredApiIdOrderByCheckedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(CheckResult.builder()
                        .id(1L)
                        .monitoredApi(api)
                        .checkedAt(Instant.parse("2026-08-08T12:00:00Z"))
                        .success(false)
                        .apiStatus(ApiStatus.DOWN)
                        .timedOut(false)
                        .httpStatus(500)
                        .latencyMs(20)
                        .errorMessage("boom")
                        .build())));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jiraTicketService.createIssue(any(MonitoredApi.class), any(Alert.class)))
                .thenReturn(Optional.of("OPS-42"));

        alertService.handleTransitionToDown(api);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, org.mockito.Mockito.atLeastOnce()).save(alertCaptor.capture());
        verify(emailNotificationService).sendDown(any(MonitoredApi.class), any(Alert.class));
        verify(jiraTicketService).createIssue(any(MonitoredApi.class), any(Alert.class));
        verify(monitoringMetrics).recordAlertOpened();

        Alert saved = alertCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(saved.getSummary()).contains("DOWN");
        assertThat(saved.getJiraIssueKey()).isEqualTo("OPS-42");
        assertThat(saved.getFailureReason()).isEqualTo("Unexpected HTTP status 500 (expected 200)");
    }

    @Test
    void capturesTimeoutAsFailureReason() {
        when(alertRepository.findByMonitoredApiIdAndStatus(api.getId(), AlertStatus.OPEN))
                .thenReturn(Optional.empty());
        when(checkResultRepository.findByMonitoredApiIdOrderByCheckedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(CheckResult.builder()
                        .monitoredApi(api)
                        .checkedAt(Instant.parse("2026-08-08T12:00:00Z"))
                        .success(false)
                        .apiStatus(ApiStatus.DOWN)
                        .timedOut(true)
                        .latencyMs(1000)
                        .build())));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertService.handleTransitionToDown(api);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getFailureReason())
                .isEqualTo("Request timed out after 1000 ms");
    }

    @Test
    void doesNotDuplicateOpenAlert() {
        Alert existing = Alert.builder()
                .id(UUID.randomUUID())
                .monitoredApi(api)
                .status(AlertStatus.OPEN)
                .openedAt(Instant.parse("2026-08-08T11:00:00Z"))
                .summary("existing")
                .jiraIssueKey("OPS-1")
                .build();
        when(alertRepository.findByMonitoredApiIdAndStatus(api.getId(), AlertStatus.OPEN))
                .thenReturn(Optional.of(existing));

        alertService.handleTransitionToDown(api);

        verify(alertRepository, never()).save(any(Alert.class));
        verify(emailNotificationService, never()).sendDown(any(), any());
    }

    @Test
    void retriesJiraCreateWhenOpenAlertMissingIssueKey() {
        Alert existing = Alert.builder()
                .id(UUID.randomUUID())
                .monitoredApi(api)
                .status(AlertStatus.OPEN)
                .openedAt(Instant.parse("2026-08-08T11:00:00Z"))
                .summary("existing")
                .jiraIssueKey(null)
                .build();
        when(alertRepository.findByMonitoredApiIdAndStatus(api.getId(), AlertStatus.OPEN))
                .thenReturn(Optional.of(existing));
        when(jiraTicketService.createIssue(api, existing)).thenReturn(Optional.of("OPS-99"));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertService.handleTransitionToDown(api);

        assertThat(existing.getJiraIssueKey()).isEqualTo("OPS-99");
        verify(jiraTicketService).createIssue(api, existing);
        verify(emailNotificationService, never()).sendDown(any(), any());
        verify(monitoringMetrics, never()).recordAlertOpened();
    }

    @Test
    void resolvesAlertCommentsJiraAndEmailsOnRecovery() {
        Alert existing = Alert.builder()
                .id(UUID.randomUUID())
                .monitoredApi(api)
                .status(AlertStatus.OPEN)
                .openedAt(Instant.parse("2026-08-08T11:00:00Z"))
                .summary("existing")
                .jiraIssueKey("OPS-1")
                .build();
        when(alertRepository.findByMonitoredApiIdAndStatus(api.getId(), AlertStatus.OPEN))
                .thenReturn(Optional.of(existing));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        api.setCurrentStatus(ApiStatus.UP);
        alertService.handleTransitionToUp(api);

        assertThat(existing.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(existing.getResolvedAt()).isNotNull();
        assertThat(existing.getDurationSeconds()).isNotNull().isPositive();
        verify(jiraTicketService).commentRecovery("OPS-1", api, existing);
        verify(emailNotificationService).sendRecovered(api, existing);
        verify(monitoringMetrics).recordAlertResolved(existing.getDurationSeconds());
        verify(jiraTicketService, never()).createIssue(any(), any());
    }
}
