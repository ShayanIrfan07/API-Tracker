package com.apitracker.integration.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.alert.entity.Alert;
import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.config.JiraProperties;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.notification.entity.NotificationLog;
import com.apitracker.notification.repository.NotificationLogRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JiraTicketServiceTest {

    @Mock
    private JiraProperties jiraProperties;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @InjectMocks
    private JiraTicketService jiraTicketService;

    private MonitoredApi api;
    private Alert alert;

    @BeforeEach
    void setUp() {
        api = MonitoredApi.builder()
                .id(UUID.randomUUID())
                .name("Payments")
                .baseUrl("https://api.example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(1000)
                .intervalSeconds(30)
                .enabled(true)
                .currentStatus(ApiStatus.DOWN)
                .build();
        alert = Alert.builder()
                .id(UUID.randomUUID())
                .monitoredApi(api)
                .status(AlertStatus.OPEN)
                .openedAt(Instant.parse("2026-08-09T08:00:00Z"))
                .summary("[API Tracker] Payments is DOWN")
                .detail("failure")
                .build();
    }

    @Test
    void createIssueSkippedWhenNotConfigured() {
        when(jiraProperties.isConfigured()).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<String> key = jiraTicketService.createIssue(api, alert);

        assertThat(key).isEmpty();
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getChannel()).isEqualTo("JIRA");
        assertThat(logCaptor.getValue().getSuccess()).isFalse();
    }

    @Test
    void commentRecoverySkippedWithoutIssueKey() {
        when(jiraProperties.isConfigured()).thenReturn(true);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        jiraTicketService.commentRecovery(null, api, alert);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getErrorMessage()).contains("No correlated Jira issue key");
        verify(jiraProperties, never()).normalizedBaseUrl();
    }

    @Test
    void browseUrlDelegatesToProperties() {
        when(jiraProperties.browseUrl("OPS-7"))
                .thenReturn(Optional.of("https://example.atlassian.net/browse/OPS-7"));

        assertThat(jiraTicketService.browseUrl("OPS-7"))
                .contains("https://example.atlassian.net/browse/OPS-7");
    }
}
