package com.apitracker.monitor.evaluation;

import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.MonitoredApi;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class StatusEvaluator {

    public EvaluationResult apply(
            MonitoredApi api,
            boolean checkSucceeded,
            Integer latencyMs,
            Instant checkedAt) {
        ApiStatus previous = api.getCurrentStatus();

        if (checkSucceeded) {
            api.setConsecutiveSuccesses(api.getConsecutiveSuccesses() + 1);
            api.setConsecutiveFailures(0);
        } else {
            api.setConsecutiveFailures(api.getConsecutiveFailures() + 1);
            api.setConsecutiveSuccesses(0);
        }

        api.setLastCheckedAt(checkedAt);

        ApiStatus next = previous;
        if (!checkSucceeded && api.getConsecutiveFailures() >= api.getFailureThreshold()) {
            next = ApiStatus.DOWN;
        } else if (checkSucceeded && api.getConsecutiveSuccesses() >= api.getSuccessThreshold()) {
            next = isLatencyDegraded(api, latencyMs) ? ApiStatus.DEGRADED : ApiStatus.UP;
        }

        boolean changed = next != previous;
        if (changed) {
            api.setCurrentStatus(next);
            api.setLastStatusChangeAt(checkedAt);
        }

        return new EvaluationResult(previous, api.getCurrentStatus(), changed);
    }

    private boolean isLatencyDegraded(MonitoredApi api, Integer latencyMs) {
        Integer threshold = api.getLatencyThresholdMs();
        return threshold != null && latencyMs != null && latencyMs > threshold;
    }

    public record EvaluationResult(ApiStatus previous, ApiStatus current, boolean changed) {
    }
}
