package com.apitracker.monitor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.evaluation.StatusEvaluator.EvaluationResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatusEvaluatorTest {

    private StatusEvaluator statusEvaluator;
    private MonitoredApi api;
    private Instant checkedAt;

    @BeforeEach
    void setUp() {
        statusEvaluator = new StatusEvaluator();
        checkedAt = Instant.parse("2026-08-08T12:00:00Z");
        api = MonitoredApi.builder()
                .id(UUID.randomUUID())
                .name("Eval API")
                .baseUrl("https://example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(1000)
                .intervalSeconds(30)
                .failureThreshold(3)
                .successThreshold(2)
                .latencyThresholdMs(200)
                .enabled(true)
                .currentStatus(ApiStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .build();
    }

    @Test
    void marksDownAfterFailureThreshold() {
        statusEvaluator.apply(api, false, null, checkedAt);
        statusEvaluator.apply(api, false, null, checkedAt.plusSeconds(1));
        assertThat(api.getCurrentStatus()).isEqualTo(ApiStatus.UNKNOWN);

        EvaluationResult result = statusEvaluator.apply(api, false, null, checkedAt.plusSeconds(2));

        assertThat(result.changed()).isTrue();
        assertThat(result.previous()).isEqualTo(ApiStatus.UNKNOWN);
        assertThat(result.current()).isEqualTo(ApiStatus.DOWN);
        assertThat(api.getConsecutiveFailures()).isEqualTo(3);
        assertThat(api.getLastStatusChangeAt()).isEqualTo(checkedAt.plusSeconds(2));
    }

    @Test
    void marksUpAfterSuccessThreshold() {
        api.setCurrentStatus(ApiStatus.DOWN);
        api.setConsecutiveFailures(3);

        statusEvaluator.apply(api, true, 50, checkedAt);
        assertThat(api.getCurrentStatus()).isEqualTo(ApiStatus.DOWN);

        EvaluationResult result = statusEvaluator.apply(api, true, 40, checkedAt.plusSeconds(1));

        assertThat(result.changed()).isTrue();
        assertThat(result.previous()).isEqualTo(ApiStatus.DOWN);
        assertThat(result.current()).isEqualTo(ApiStatus.UP);
        assertThat(api.getConsecutiveSuccesses()).isEqualTo(2);
    }

    @Test
    void marksDegradedWhenLatencyExceedsThreshold() {
        statusEvaluator.apply(api, true, 250, checkedAt);
        EvaluationResult result = statusEvaluator.apply(api, true, 300, checkedAt.plusSeconds(1));

        assertThat(result.changed()).isTrue();
        assertThat(result.current()).isEqualTo(ApiStatus.DEGRADED);
    }

    @Test
    void successResetsFailureStreak() {
        statusEvaluator.apply(api, false, null, checkedAt);
        statusEvaluator.apply(api, false, null, checkedAt.plusSeconds(1));
        EvaluationResult result = statusEvaluator.apply(api, true, 20, checkedAt.plusSeconds(2));

        assertThat(result.changed()).isFalse();
        assertThat(api.getConsecutiveFailures()).isZero();
        assertThat(api.getConsecutiveSuccesses()).isEqualTo(1);
        assertThat(api.getCurrentStatus()).isEqualTo(ApiStatus.UNKNOWN);
    }
}
