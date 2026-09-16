package com.apitracker.monitor.metrics;

import com.apitracker.monitor.entity.ApiStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class MonitoringMetrics {

    private final MeterRegistry meterRegistry;

    public MonitoringMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordCheck(boolean success, Integer latencyMs) {
        Counter.builder("api_tracker_checks_total")
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();

        if (latencyMs != null) {
            Timer.builder("api_tracker_check_latency")
                    .register(meterRegistry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
        }
    }

    public void recordStatusTransition(ApiStatus from, ApiStatus to) {
        Counter.builder("api_tracker_status_transitions_total")
                .tag("from", from.name())
                .tag("to", to.name())
                .register(meterRegistry)
                .increment();
    }

    public void recordAlertOpened() {
        Counter.builder("api_tracker_alerts_opened_total")
                .register(meterRegistry)
                .increment();
    }

    public void recordAlertResolved(Long durationSeconds) {
        Counter.builder("api_tracker_alerts_resolved_total")
                .register(meterRegistry)
                .increment();
        if (durationSeconds != null && durationSeconds >= 0) {
            Timer.builder("api_tracker_incident_duration")
                    .register(meterRegistry)
                    .record(durationSeconds, TimeUnit.SECONDS);
        }
    }

    public void recordRetentionDeleted(long count) {
        Counter.builder("api_tracker_check_results_purged_total")
                .register(meterRegistry)
                .increment(count);
    }

    public void recordCheckDuration(Duration duration) {
        Timer.builder("api_tracker_check_duration")
                .register(meterRegistry)
                .record(duration);
    }
}
