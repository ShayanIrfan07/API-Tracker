package com.apitracker.alert.service;

import com.apitracker.alert.dto.AlertResponse;
import com.apitracker.alert.entity.Alert;
import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.alert.repository.AlertRepository;
import com.apitracker.config.JiraProperties;
import com.apitracker.exception.ResourceNotFoundException;
import com.apitracker.integration.jira.JiraTicketService;
import com.apitracker.monitor.entity.CheckResult;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.metrics.MonitoringMetrics;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.notification.service.EmailNotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AlertService {

    private final AlertRepository alertRepository;
    private final CheckResultRepository checkResultRepository;
    private final JiraTicketService jiraTicketService;
    private final JiraProperties jiraProperties;
    private final EmailNotificationService emailNotificationService;
    private final MonitoringMetrics monitoringMetrics;

    public void handleTransitionToDown(MonitoredApi api) {
        Optional<Alert> existingOpen = alertRepository.findByMonitoredApiIdAndStatus(api.getId(), AlertStatus.OPEN);
        if (existingOpen.isPresent()) {
            Alert open = existingOpen.get();
            if (open.getJiraIssueKey() == null) {
                jiraTicketService.createIssue(api, open).ifPresent(key -> {
                    open.setJiraIssueKey(key);
                    alertRepository.save(open);
                });
            }
            log.debug("Open alert already exists for apiId={}; skipping duplicate notify", api.getId());
            return;
        }

        String detail = buildFailureDetail(api);
        Alert alert = Alert.builder()
                .id(UUID.randomUUID())
                .monitoredApi(api)
                .status(AlertStatus.OPEN)
                .openedAt(Instant.now())
                .summary("[API Tracker] " + api.getName() + " is DOWN")
                .detail(detail)
                .build();
        alertRepository.save(alert);
        alertRepository.flush();

        jiraTicketService.createIssue(api, alert).ifPresent(key -> {
            alert.setJiraIssueKey(key);
            alertRepository.save(alert);
        });

        emailNotificationService.sendDown(api, alert);
        monitoringMetrics.recordAlertOpened();
        log.info("Opened alert {} for apiId={}", alert.getId(), api.getId());
    }

    public void handleTransitionToUp(MonitoredApi api) {
        Optional<Alert> existingOpen = alertRepository.findByMonitoredApiIdAndStatus(api.getId(), AlertStatus.OPEN);
        if (existingOpen.isEmpty()) {
            log.debug("No open alert to resolve for apiId={}", api.getId());
            return;
        }

        Alert alert = existingOpen.get();
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        alertRepository.save(alert);
        alertRepository.flush();

        jiraTicketService.commentRecovery(alert.getJiraIssueKey(), api, alert);
        emailNotificationService.sendRecovered(api, alert);
        log.info("Resolved alert {} for apiId={}", alert.getId(), api.getId());
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findAll(AlertStatus status) {
        List<Alert> alerts = status == null
                ? alertRepository.findAllByOrderByOpenedAtDesc()
                : alertRepository.findByStatusOrderByOpenedAtDesc(status);
        return alerts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse findById(UUID id) {
        return toResponse(alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id)));
    }

    private String buildFailureDetail(MonitoredApi api) {
        List<CheckResult> recent = checkResultRepository
                .findByMonitoredApiIdOrderByCheckedAtDesc(api.getId(), PageRequest.of(0, 5))
                .getContent();

        if (recent.isEmpty()) {
            return "No check results available.";
        }

        return recent.stream()
                .map(result -> "%s success=%s httpStatus=%s latencyMs=%s error=%s".formatted(
                        result.getCheckedAt(),
                        result.getSuccess(),
                        result.getHttpStatus(),
                        result.getLatencyMs(),
                        result.getErrorMessage() == null ? "-" : result.getErrorMessage()))
                .collect(Collectors.joining("\n"));
    }

    private AlertResponse toResponse(Alert alert) {
        String key = alert.getJiraIssueKey();
        return new AlertResponse(
                alert.getId(),
                alert.getMonitoredApi().getId(),
                alert.getMonitoredApi().getName(),
                alert.getStatus(),
                alert.getOpenedAt(),
                alert.getResolvedAt(),
                key,
                jiraProperties.browseUrl(key).orElse(null),
                alert.getSummary(),
                alert.getDetail());
    }
}
