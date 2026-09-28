package com.apitracker.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.alert.entity.Alert;
import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.config.MailProperties;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.entity.CheckResult;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.notification.entity.NotificationLog;
import com.apitracker.notification.repository.NotificationLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailNotificationServiceTest {

    @Mock
    private MailProperties mailProperties;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private RestClient restClient;

    @Mock
    private CheckResultRepository checkResultRepository;

    @InjectMocks
    private EmailNotificationService emailNotificationService;

    private MonitoredApi api;
    private Alert alert;

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
                .ownerEmail("oncall@example.com")
                .enabled(true)
                .currentStatus(ApiStatus.DOWN)
                .build();
        alert = Alert.builder()
                .id(UUID.randomUUID())
                .monitoredApi(api)
                .status(AlertStatus.OPEN)
                .openedAt(Instant.parse("2026-08-09T08:00:00Z"))
                .summary("[API Tracker] Payments API is DOWN")
                .detail("recent failures")
                .failureReason("Connection refused")
                .build();
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(checkResultRepository.findByMonitoredApiIdOrderByCheckedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(CheckResult.builder()
                        .httpStatus(500)
                        .latencyMs(850)
                        .build())));
    }

    @Test
    void sendDownDeliversEmailWhenMailConfigured() {
        when(mailProperties.isConfigured()).thenReturn(true);
        when(mailProperties.usesBrevo()).thenReturn(false);
        when(mailProperties.from()).thenReturn("alerts@apitracker.local");
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        emailNotificationService.sendDown(api, alert);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("alerts@apitracker.local");
        assertThat(message.getTo()).containsExactly("oncall@example.com");
        assertThat(message.getSubject()).isEqualTo("[API TRACKER] API DOWN - Payments API");
        assertThat(message.getText()).contains("Incident ID:");
        assertThat(message.getText()).contains("Expected Status: 200");
        assertThat(message.getText()).contains("Actual Status: 500");
        assertThat(message.getText()).contains("Latency: 850 ms");
        assertThat(message.getText()).contains("Connection refused");

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getSuccess()).isTrue();
        assertThat(logCaptor.getValue().getChannel()).isEqualTo("EMAIL");
    }

    @Test
    void sendRecoveredIncludesDurationWhenMailConfigured() {
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.parse("2026-08-09T08:05:00Z"));
        alert.setDurationSeconds(300L);
        when(mailProperties.isConfigured()).thenReturn(true);
        when(mailProperties.usesBrevo()).thenReturn(false);
        when(mailProperties.from()).thenReturn("alerts@apitracker.local");
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        emailNotificationService.sendRecovered(api, alert);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("[API TRACKER] API RECOVERED - Payments API");
        assertThat(messageCaptor.getValue().getText()).contains("Incident Duration: 5m");
        assertThat(messageCaptor.getValue().getText()).contains("Incident ID:");
    }

    @Test
    void skipsSendWhenMailDisabled() {
        when(mailProperties.isConfigured()).thenReturn(false);

        emailNotificationService.sendDown(api, alert);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(restClient, never()).post();
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getSuccess()).isFalse();
        assertThat(logCaptor.getValue().getErrorMessage()).contains("disabled");
    }

    @Test
    void skipsSendWhenOwnerEmailMissing() {
        api.setOwnerEmail(null);

        emailNotificationService.sendDown(api, alert);

        verify(mailProperties, never()).isConfigured();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRecipient()).isNull();
        assertThat(logCaptor.getValue().getErrorMessage()).contains("ownerEmail");
    }

    @Test
    void skipsSendWhenMailSenderBeanUnavailable() {
        when(mailProperties.isConfigured()).thenReturn(true);
        when(mailProperties.usesBrevo()).thenReturn(false);
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        emailNotificationService.sendDown(api, alert);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getSuccess()).isFalse();
        assertThat(logCaptor.getValue().getErrorMessage()).contains("JavaMailSender");
    }

    @Test
    void recordsFailureWhenSmtpSendFails() {
        when(mailProperties.isConfigured()).thenReturn(true);
        when(mailProperties.usesBrevo()).thenReturn(false);
        when(mailProperties.from()).thenReturn("alerts@apitracker.local");
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new RuntimeException("Authentication failed: bad password secret-token"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        emailNotificationService.sendDown(api, alert);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getSuccess()).isFalse();
        assertThat(logCaptor.getValue().getErrorMessage()).contains("Authentication failed");
    }
}
