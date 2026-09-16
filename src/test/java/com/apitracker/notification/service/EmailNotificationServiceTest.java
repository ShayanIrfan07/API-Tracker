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
import com.apitracker.notification.entity.NotificationLog;
import com.apitracker.notification.repository.NotificationLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
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
        assertThat(message.getSubject()).contains("DOWN");
        assertThat(message.getText()).contains("Payments API");
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
        assertThat(messageCaptor.getValue().getSubject()).contains("RECOVERED");
        assertThat(messageCaptor.getValue().getText()).contains("5m");
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
