package com.apitracker.monitor.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.config.CheckProperties;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import com.apitracker.monitor.service.HealthCheckService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledCheckRunnerTest {

    @Mock
    private CheckProperties checkProperties;

    @Mock
    private MonitoredApiRepository monitoredApiRepository;

    @Mock
    private HealthCheckService healthCheckService;

    @Mock
    private Executor checkTaskExecutor;

    private ScheduledCheckRunner scheduledCheckRunner;

    @BeforeEach
    void setUp() {
        // The constructor uses a @Qualifier(CheckExecutorConfig.CHECK_TASK_EXECUTOR) on the
        // Executor argument, which Mockito's @InjectMocks cannot resolve by type alone.
        // Construct the instance manually to wire the qualified executor mock explicitly.
        scheduledCheckRunner =
                new ScheduledCheckRunner(checkProperties, monitoredApiRepository, healthCheckService, checkTaskExecutor);
    }

    private MonitoredApi buildApi(UUID id, Instant lastCheckedAt, int intervalSeconds) {
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        return MonitoredApi.builder()
                .id(id)
                .name("Test API")
                .baseUrl("https://api.example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(1000)
                .intervalSeconds(intervalSeconds)
                .failureThreshold(3)
                .successThreshold(2)
                .enabled(true)
                .currentStatus(ApiStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .lastCheckedAt(lastCheckedAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void tickReturnsEarlyAndNeverDispatchesWhenSchedulerDisabled() {
        when(checkProperties.schedulerEnabled()).thenReturn(false);

        scheduledCheckRunner.tick();

        verify(monitoredApiRepository, never()).findByEnabledTrue();
        verify(checkTaskExecutor, never()).execute(any(Runnable.class));
        verify(healthCheckService, never()).runScheduledCheck(any(UUID.class));
    }

    @Test
    void tickReturnsEarlyAndNeverDispatchesWhenNoEnabledApis() {
        when(checkProperties.schedulerEnabled()).thenReturn(true);
        when(monitoredApiRepository.findByEnabledTrue()).thenReturn(List.of());

        scheduledCheckRunner.tick();

        verify(checkTaskExecutor, never()).execute(any(Runnable.class));
        verify(healthCheckService, never()).runScheduledCheck(any(UUID.class));
    }

    @Test
    void tickDispatchesApiWithNullLastCheckedAtToExecutor() {
        UUID apiId = UUID.randomUUID();
        MonitoredApi neverChecked = buildApi(apiId, null, 60);

        when(checkProperties.schedulerEnabled()).thenReturn(true);
        when(monitoredApiRepository.findByEnabledTrue()).thenReturn(List.of(neverChecked));

        scheduledCheckRunner.tick();

        verify(checkTaskExecutor, times(1)).execute(any(Runnable.class));
        verify(healthCheckService, never()).runScheduledCheck(any(UUID.class));
    }

    @Test
    void tickDispatchesDueApiAndSkipsNotDueApiInSameResult() {
        UUID dueApiId = UUID.randomUUID();
        UUID notDueApiId = UUID.randomUUID();
        // Due: lastCheckedAt far in the past, so lastCheckedAt + intervalSeconds is well before now.
        MonitoredApi dueApi = buildApi(dueApiId, Instant.parse("2020-01-01T00:00:00Z"), 60);
        // Not due: lastCheckedAt far in the future, so nextCheckAt is after now.
        MonitoredApi notDueApi = buildApi(notDueApiId, Instant.parse("2099-01-01T00:00:00Z"), 60);

        when(checkProperties.schedulerEnabled()).thenReturn(true);
        when(monitoredApiRepository.findByEnabledTrue()).thenReturn(List.of(dueApi, notDueApi));

        scheduledCheckRunner.tick();

        // Only the due API should be dispatched — exactly one executor.execute invocation.
        verify(checkTaskExecutor, times(1)).execute(any(Runnable.class));
        verify(healthCheckService, never()).runScheduledCheck(any(UUID.class));
    }

    @Test
    void tickDispatchedRunnableInvokesRunScheduledCheckWithApiId() {
        UUID apiId = UUID.randomUUID();
        MonitoredApi dueApi = buildApi(apiId, Instant.parse("2020-01-01T00:00:00Z"), 60);

        when(checkProperties.schedulerEnabled()).thenReturn(true);
        when(monitoredApiRepository.findByEnabledTrue()).thenReturn(List.of(dueApi));

        scheduledCheckRunner.tick();

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(checkTaskExecutor, times(1)).execute(runnableCaptor.capture());

        // The executor is mocked, so the Runnable is never actually run by it. Run it manually
        // to assert that it delegates to healthCheckService.runScheduledCheck(apiId).
        Runnable dispatched = runnableCaptor.getValue();
        assertThat(dispatched).isNotNull();
        dispatched.run();

        verify(healthCheckService, times(1)).runScheduledCheck(apiId);
    }

    @Test
    void tickDispatchesMultipleDueApisEachOnce() {
        UUID firstApiId = UUID.randomUUID();
        UUID secondApiId = UUID.randomUUID();
        MonitoredApi firstDue = buildApi(firstApiId, Instant.parse("2020-01-01T00:00:00Z"), 30);
        MonitoredApi secondDue = buildApi(secondApiId, null, 30);

        when(checkProperties.schedulerEnabled()).thenReturn(true);
        when(monitoredApiRepository.findByEnabledTrue()).thenReturn(List.of(firstDue, secondDue));

        scheduledCheckRunner.tick();

        verify(checkTaskExecutor, times(2)).execute(any(Runnable.class));
        verify(healthCheckService, never()).runScheduledCheck(any(UUID.class));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(checkTaskExecutor, times(2)).execute(runnableCaptor.capture());
        List<Runnable> dispatched = runnableCaptor.getAllValues();
        assertThat(dispatched).hasSize(2);

        dispatched.get(0).run();
        dispatched.get(1).run();

        verify(healthCheckService, times(1)).runScheduledCheck(firstApiId);
        verify(healthCheckService, times(1)).runScheduledCheck(secondApiId);
    }
}
