package com.apitracker.integration.jira;

import com.apitracker.alert.entity.Alert;
import com.apitracker.config.JiraProperties;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.notification.entity.NotificationLog;
import com.apitracker.notification.repository.NotificationLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Creates Jira issues on outage, stores the issue key on the alert (correlation),
 * and posts a recovery comment on UP — without auto-closing the issue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JiraTicketService {

    private final JiraProperties jiraProperties;
    private final NotificationLogRepository notificationLogRepository;

    public Optional<String> createIssue(MonitoredApi api, Alert alert) {
        if (!jiraProperties.isConfigured()) {
            log.info("Jira integration disabled or incomplete; skipping ticket creation for alert {}", alert.getId());
            saveLog(alert, null, false, "Jira integration disabled or incomplete configuration");
            return Optional.empty();
        }

        try {
            RestClient client = buildClient();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("project", Map.of("key", jiraProperties.projectKey()));
            fields.put("summary", alert.getSummary());
            fields.put("description", buildDescription(api, alert));
            fields.put("issuetype", Map.of("name", jiraProperties.issueType()));
            fields.put("priority", Map.of("name", jiraProperties.priority()));
            fields.put("labels", correlationLabels(api, alert));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/rest/api/2/issue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", fields))
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("key") == null) {
                log.warn("Jira create issue returned empty key for alert {}", alert.getId());
                saveLog(alert, jiraProperties.projectKey(), false, "Empty issue key in Jira response");
                return Optional.empty();
            }

            String key = response.get("key").toString();
            saveLog(alert, key, true, null);
            log.info("Created Jira issue {} for alert {} (apiId={})", key, alert.getId(), api.getId());
            return Optional.of(key);
        } catch (RestClientException ex) {
            log.error("Failed to create Jira issue for alert {}: {}", alert.getId(), ex.getMessage());
            saveLog(alert, jiraProperties.projectKey(), false, ex.getMessage());
            return Optional.empty();
        }
    }

    public void commentRecovery(String issueKey, MonitoredApi api, Alert alert) {
        if (!jiraProperties.isConfigured()) {
            saveLog(alert, issueKey, false, "Jira integration disabled or incomplete configuration");
            return;
        }
        if (issueKey == null || issueKey.isBlank()) {
            saveLog(alert, null, false, "No correlated Jira issue key on alert");
            return;
        }

        try {
            RestClient client = buildClient();
            String comment = """
                    API Tracker recovery notice

                    Correlation:
                    - Alert ID: %s
                    - API ID: %s
                    - Jira issue: %s

                    API: %s
                    URL: %s%s
                    Alert opened at: %s
                    Alert resolved at: %s

                    Status is UP again. This issue was NOT auto-closed; please review and close manually if appropriate.
                    """.formatted(
                    alert.getId(),
                    api.getId(),
                    issueKey,
                    api.getName(),
                    api.getBaseUrl(),
                    api.getPath(),
                    alert.getOpenedAt(),
                    alert.getResolvedAt());

            client.post()
                    .uri("/rest/api/2/issue/{key}/comment", issueKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("body", comment))
                    .retrieve()
                    .toBodilessEntity();

            saveLog(alert, issueKey, true, null);
            log.info("Added recovery comment to Jira issue {} for alert {}", issueKey, alert.getId());
        } catch (RestClientException ex) {
            log.error("Failed to comment on Jira issue {} for alert {}: {}", issueKey, alert.getId(), ex.getMessage());
            saveLog(alert, issueKey, false, ex.getMessage());
        }
    }

    public Optional<String> browseUrl(String issueKey) {
        return jiraProperties.browseUrl(issueKey);
    }

    RestClient buildClient() {
        String credentials = jiraProperties.username() + ":" + jiraProperties.apiToken();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);

        return RestClient.builder()
                .baseUrl(jiraProperties.normalizedBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Basic " + basicAuth)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private List<String> correlationLabels(MonitoredApi api, Alert alert) {
        return List.of(
                "api-tracker",
                "api-" + compact(api.getId()),
                "alert-" + compact(alert.getId())
        );
    }

    private static String compact(java.util.UUID id) {
        return id.toString().replace("-", "");
    }

    private String buildDescription(MonitoredApi api, Alert alert) {
        return """
                API Tracker detected an outage.

                Correlation:
                - Alert ID: %s
                - API ID: %s
                - API name: %s

                Check:
                - Method: %s
                - URL: %s%s
                - Expected status: %s
                - Opened at: %s

                Recent check details:
                %s
                """.formatted(
                alert.getId(),
                api.getId(),
                api.getName(),
                api.getHttpMethod(),
                api.getBaseUrl(),
                api.getPath(),
                api.getExpectedStatusCode(),
                alert.getOpenedAt(),
                alert.getDetail() == null ? "(none)" : alert.getDetail());
    }

    private void saveLog(Alert alert, String recipient, boolean success, String errorMessage) {
        notificationLogRepository.save(NotificationLog.builder()
                .alert(alert)
                .channel("JIRA")
                .recipient(recipient)
                .success(success)
                .errorMessage(truncate(errorMessage))
                .sentAt(Instant.now())
                .build());
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
