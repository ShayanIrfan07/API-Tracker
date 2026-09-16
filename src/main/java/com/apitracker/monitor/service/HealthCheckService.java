package com.apitracker.monitor.service;

import com.apitracker.alert.service.AlertService;
import com.apitracker.exception.ResourceNotFoundException;
import com.apitracker.monitor.checker.HttpCheckClient;
import com.apitracker.monitor.checker.HttpCheckOutcome;
import com.apitracker.monitor.dto.CheckNowResponse;
import com.apitracker.monitor.dto.CheckResultResponse;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.CheckResult;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.evaluation.StatusEvaluator;
import com.apitracker.monitor.evaluation.StatusEvaluator.EvaluationResult;
import com.apitracker.monitor.metrics.MonitoringMetrics;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckService {

    private final MonitoredApiRepository monitoredApiRepository;
    private final CheckResultRepository checkResultRepository;
    private final HttpCheckClient httpCheckClient;
    private final StatusEvaluator statusEvaluator;
    private final AlertService alertService;
    private final MonitoringMetrics monitoringMetrics;

    private final ConcurrentMap<UUID, Boolean> inFlight = new ConcurrentHashMap<>();

    public boolean tryBegin(UUID apiId) {
        return inFlight.putIfAbsent(apiId, Boolean.TRUE) == null;
    }

    public void end(UUID apiId) {
        inFlight.remove(apiId);
    }

    @Transactional
    public CheckNowResponse checkNow(UUID apiId) {
        if (!tryBegin(apiId)) {
            throw new IllegalStateException("A check is already in progress for API: " + apiId);
        }
        try {
            return executeCheck(apiId);
        } finally {
            end(apiId);
        }
    }

    @Transactional
    public void runScheduledCheck(UUID apiId) {
        if (!tryBegin(apiId)) {
            log.debug("Skipping apiId={} because a check is already in flight", apiId);
            return;
        }
        try {
            executeCheck(apiId);
        } catch (ResourceNotFoundException ex) {
            log.debug("API {} no longer exists; skipping scheduled check", apiId);
        } catch (Exception ex) {
            log.error("Scheduled check failed for apiId={}", apiId, ex);
        } finally {
            end(apiId);
        }
    }

    @Transactional(readOnly = true)
    public Page<CheckResultResponse> getCheckHistory(UUID apiId, Pageable pageable) {
        if (!monitoredApiRepository.existsById(apiId)) {
            throw new ResourceNotFoundException("MonitoredApi", apiId);
        }
        return checkResultRepository.findByMonitoredApiIdOrderByCheckedAtDesc(apiId, pageable)
                .map(result -> toCheckResponse(apiId, result));
    }

    private CheckNowResponse executeCheck(UUID apiId) {
        MonitoredApi api = monitoredApiRepository.findById(apiId)
                .orElseThrow(() -> new ResourceNotFoundException("MonitoredApi", apiId));

        log.debug("Executing HTTP check apiId={} name='{}' method={} url={}{}",
                api.getId(),
                api.getName(),
                api.getHttpMethod(),
                api.getBaseUrl(),
                api.getPath());

        Instant started = Instant.now();
        HttpCheckOutcome outcome = httpCheckClient.check(api);
        Instant checkedAt = Instant.now();

        EvaluationResult evaluation = statusEvaluator.apply(
                api, outcome.success(), outcome.latencyMs(), checkedAt);

        CheckResult result = CheckResult.builder()
                .monitoredApi(api)
                .checkedAt(checkedAt)
                .success(outcome.success())
                .httpStatus(outcome.httpStatus())
                .latencyMs(outcome.latencyMs())
                .errorMessage(outcome.errorMessage())
                .apiStatus(api.getCurrentStatus())
                .timedOut(outcome.timedOut())
                .build();
        result = checkResultRepository.save(result);
        checkResultRepository.flush();

        monitoredApiRepository.save(api);
        monitoredApiRepository.flush();

        monitoringMetrics.recordCheck(outcome.success(), outcome.latencyMs());
        monitoringMetrics.recordCheckDuration(Duration.between(started, checkedAt));

        if (evaluation.changed()) {
            monitoringMetrics.recordStatusTransition(evaluation.previous(), evaluation.current());
            if (evaluation.current() == ApiStatus.DOWN) {
                alertService.handleTransitionToDown(api);
            } else if (evaluation.current() == ApiStatus.UP && evaluation.previous() == ApiStatus.DOWN) {
                alertService.handleTransitionToUp(api);
            } else if (evaluation.current() == ApiStatus.DEGRADED && evaluation.previous() == ApiStatus.DOWN) {
                // Recovered enough to respond, but still slow — treat as recovery of outage.
                alertService.handleTransitionToUp(api);
            }
        }

        if (outcome.success()) {
            log.info(
                    "API check completed apiId={} name='{}' status={} httpStatus={} latencyMs={}",
                    api.getId(),
                    api.getName(),
                    api.getCurrentStatus(),
                    outcome.httpStatus(),
                    outcome.latencyMs());
        } else {
            log.warn(
                    "API check failed apiId={} name='{}' status={} httpStatus={} latencyMs={} error={}",
                    api.getId(),
                    api.getName(),
                    api.getCurrentStatus(),
                    outcome.httpStatus(),
                    outcome.latencyMs(),
                    outcome.errorMessage());
        }

        return toCheckNowResponse(api, outcome, result);
    }

    private CheckNowResponse toCheckNowResponse(MonitoredApi api, HttpCheckOutcome outcome, CheckResult result) {
        return new CheckNowResponse(
                api.getId(),
                api.getName(),
                api.getCurrentStatus(),
                outcome.success(),
                outcome.httpStatus(),
                outcome.latencyMs(),
                outcome.timedOut(),
                outcome.errorMessage(),
                result.getCheckedAt(),
                result.getId());
    }

    private CheckResultResponse toCheckResponse(UUID apiId, CheckResult result) {
        return new CheckResultResponse(
                result.getId(),
                apiId,
                result.getCheckedAt(),
                result.getSuccess(),
                result.getApiStatus(),
                result.getHttpStatus(),
                result.getLatencyMs(),
                result.getTimedOut(),
                result.getErrorMessage());
    }
}
