package com.apitracker.notification.service;

import com.apitracker.alert.entity.Alert;
import com.apitracker.config.MailProperties;
import com.apitracker.monitor.entity.CheckResult;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.notification.entity.NotificationLog;
import com.apitracker.notification.repository.NotificationLogRepository;
import com.apitracker.monitor.repository.CheckResultRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
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
    private final CheckResultRepository checkResultRepository;
    private final RestClient restClient;

    public void sendDown(MonitoredApi api, Alert alert) {
        CheckSnapshot check = latestCheck(api.getId());
        String subject = "[API TRACKER] API DOWN - " + api.getName();
        String body = """
                API Name: %s
                API URL: %s
                Status: DOWN
                Expected Status: %s
                Actual Status: %s
                Latency: %s
                Failure Reason: %s
                Incident ID: %s
                Detected At: %s

                Recent check history:
                %s
                """.formatted(
                api.getName(),
                api.getBaseUrl() + api.getPath(),
                api.getExpectedStatusCode(),
                formatActualStatus(check),
                formatLatency(check),
                alert.getFailureReason() == null ? "(unknown)" : alert.getFailureReason(),
                alert.getId(),
                alert.getOpenedAt(),
                alert.getDetail() == null ? "(none)" : alert.getDetail());
        send(api, alert, subject, body);
    }

    public void sendRecovered(MonitoredApi api, Alert alert) {
        String subject = "[API TRACKER] API RECOVERED - " + api.getName();
        String body = """
                API Name: %s
                API URL: %s
                Previous Status: DOWN
                Current Status: %s
                Recovery Time: %s
                Incident Duration: %s
                Incident ID: %s
                """.formatted(
                api.getName(),
                api.getBaseUrl() + api.getPath(),
                api.getCurrentStatus(),
                alert.getResolvedAt(),
                formatDuration(alert.getDurationSeconds()),
                alert.getId());
        send(api, alert, subject, body);
    }

    private void send(MonitoredApi api, Alert alert, String subject, String body) {
        String recipient = api.getOwnerEmail();
        if (!StringUtils.hasText(recipient)) {
            log.warn(
                    "Notification skipped: no ownerEmail for apiId={} alertId={}; monitoring continues",
                    api.getId(),
                    alert.getId());
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
                    "Alert notification sent alertId={} apiId={} recipient={} provider={}",
                    alert.getId(),
                    api.getId(),
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

    private CheckSnapshot latestCheck(UUID apiId) {
        List<CheckResult> recent = checkResultRepository
                .findByMonitoredApiIdOrderByCheckedAtDesc(apiId, PageRequest.of(0, 1))
                .getContent();
        if (recent.isEmpty()) {
            return CheckSnapshot.empty();
        }
        CheckResult latest = recent.getFirst();
        return new CheckSnapshot(latest.getHttpStatus(), latest.getLatencyMs());
    }

    private static String formatActualStatus(CheckSnapshot check) {
        return check.httpStatus() == null ? "(none)" : String.valueOf(check.httpStatus());
    }

    private static String formatLatency(CheckSnapshot check) {
        return check.latencyMs() == null ? "(unknown)" : check.latencyMs() + " ms";
    }

    private record CheckSnapshot(Integer httpStatus, Integer latencyMs) {
        static CheckSnapshot empty() {
            return new CheckSnapshot(null, null);
        }
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
