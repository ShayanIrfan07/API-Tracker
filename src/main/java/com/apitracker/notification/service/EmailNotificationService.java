package com.apitracker.notification.service;

import com.apitracker.alert.entity.Alert;
import com.apitracker.config.MailProperties;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.notification.entity.NotificationLog;
import com.apitracker.notification.repository.NotificationLogRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Sends incident lifecycle emails only (open on DOWN, recovery on UP).
 * Monitoring continues when {@code MAIL_ENABLED=false} or email is misconfigured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 255;

    private final MailProperties mailProperties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationLogRepository notificationLogRepository;
    private final RestClient restClient;

    public void sendDown(MonitoredApi api, Alert alert) {
        String subject = "[API Tracker] " + api.getName() + " DOWN";
        String body = """
                API Tracker alert: DOWN

                API: %s
                URL: %s%s
                Opened at: %s
                Failure reason: %s
                Jira: %s

                Details:
                %s
                """.formatted(
                api.getName(),
                api.getBaseUrl(),
                api.getPath(),
                alert.getOpenedAt(),
                alert.getFailureReason() == null ? "(unknown)" : alert.getFailureReason(),
                alert.getJiraIssueKey() == null ? "(not created)" : alert.getJiraIssueKey(),
                alert.getDetail() == null ? "(none)" : alert.getDetail());
        send(api, alert, subject, body);
    }

    public void sendRecovered(MonitoredApi api, Alert alert) {
        String subject = "[API Tracker] " + api.getName() + " RECOVERED";
        String body = """
                API Tracker alert: RECOVERED

                API: %s
                URL: %s%s
                Opened at: %s
                Resolved at: %s
                Duration: %s
                Jira: %s

                The API is UP again. The Jira issue was commented on but not auto-closed.
                """.formatted(
                api.getName(),
                api.getBaseUrl(),
                api.getPath(),
                alert.getOpenedAt(),
                alert.getResolvedAt(),
                formatDuration(alert.getDurationSeconds()),
                alert.getJiraIssueKey() == null ? "(not created)" : alert.getJiraIssueKey());
        send(api, alert, subject, body);
    }

    private void send(MonitoredApi api, Alert alert, String subject, String body) {
        String recipient = api.getOwnerEmail();
        if (!StringUtils.hasText(recipient)) {
            log.info("No ownerEmail configured for API {}; skipping email for alert {}", api.getId(), alert.getId());
            saveLog(alert, null, false, "No ownerEmail configured");
            return;
        }

        if (!mailProperties.isConfigured()) {
            log.info("Email integration disabled or incomplete; skipping email for alert {}", alert.getId());
            saveLog(alert, recipient, false, "Email integration disabled or incomplete configuration");
            return;
        }

        try {
            if (mailProperties.usesBrevo()) {
                sendViaBrevo(recipient, subject, body);
            } else {
                sendViaSmtp(recipient, subject, body);
            }

            saveLog(alert, recipient, true, null);
            log.info(
                    "Sent email for alert {} to {} using {}",
                    alert.getId(),
                    recipient,
                    mailProperties.provider());
        } catch (Exception ex) {
            String safeMessage = truncateError(ex.getMessage());
            log.error("Failed to send email for alert {} to {}: {}", alert.getId(), recipient, safeMessage);
            saveLog(alert, recipient, false, safeMessage);
        }
    }

    private void sendViaSmtp(String recipient, String subject, String body) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("JavaMailSender bean not available");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.from());
        message.setTo(recipient.trim());
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private void sendViaBrevo(String recipient, String subject, String body) {
        MailProperties.BrevoProperties brevo = mailProperties.brevo();
        if (brevo == null) {
            throw new IllegalStateException("Brevo configuration is missing");
        }
        if (!StringUtils.hasText(brevo.apiUrl())) {
            throw new IllegalStateException("Brevo API URL is missing");
        }
        if (!StringUtils.hasText(brevo.apiKey())) {
            throw new IllegalStateException("Brevo API key is missing");
        }

        BrevoEmailRequest request = new BrevoEmailRequest(
                new BrevoSender("API Tracker", mailProperties.from()),
                List.of(new BrevoRecipient(recipient.trim())),
                subject,
                body);

        restClient.post()
                .uri(brevo.apiUrl())
                .header("accept", "application/json")
                .header("api-key", brevo.apiKey())
                .header("content-type", "application/json")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private static String truncateError(String message) {
        if (message == null || message.isBlank()) {
            return "Email send failed";
        }
        return message.length() <= ERROR_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }

    private static String formatDuration(Long durationSeconds) {
        if (durationSeconds == null) {
            return "(unknown)";
        }
        if (durationSeconds < 60) {
            return durationSeconds + "s";
        }
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;
        return seconds == 0 ? minutes + "m" : minutes + "m " + seconds + "s";
    }

    private void saveLog(Alert alert, String recipient, boolean success, String errorMessage) {
        notificationLogRepository.save(NotificationLog.builder()
                .alert(alert)
                .channel("EMAIL")
                .recipient(recipient)
                .success(success)
                .errorMessage(errorMessage)
                .sentAt(Instant.now())
                .build());
    }

    private record BrevoEmailRequest(
            BrevoSender sender,
            List<BrevoRecipient> to,
            String subject,
            String textContent) {
    }

    private record BrevoSender(String name, String email) {
    }

    private record BrevoRecipient(String email) {
    }
}
