package com.apitracker.monitor.scheduler;

import com.apitracker.config.CheckExecutorConfig;
import com.apitracker.config.CheckProperties;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import com.apitracker.monitor.service.HealthCheckService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ScheduledCheckRunner {

    private final CheckProperties checkProperties;
    private final MonitoredApiRepository monitoredApiRepository;
    private final HealthCheckService healthCheckService;
    private final Executor checkTaskExecutor;

    public ScheduledCheckRunner(
            CheckProperties checkProperties,
            MonitoredApiRepository monitoredApiRepository,
            HealthCheckService healthCheckService,
            @Qualifier(CheckExecutorConfig.CHECK_TASK_EXECUTOR) Executor checkTaskExecutor) {
        this.checkProperties = checkProperties;
        this.monitoredApiRepository = monitoredApiRepository;
        this.healthCheckService = healthCheckService;
        this.checkTaskExecutor = checkTaskExecutor;
    }

    @Scheduled(fixedDelayString = "${app.check.tick-ms:5000}")
    public void tick() {
        if (!checkProperties.schedulerEnabled()) {
            return;
        }

        Instant now = Instant.now();
        List<MonitoredApi> dueApis = monitoredApiRepository.findByEnabledTrue().stream()
                .filter(api -> isDue(api, now))
                .toList();

        if (dueApis.isEmpty()) {
            return;
        }

        log.info("Scheduler dispatching {} due API check(s)", dueApis.size());
        for (MonitoredApi api : dueApis) {
            UUID apiId = api.getId();
            String apiName = api.getName();
            checkTaskExecutor.execute(() -> {
                log.info("API check started apiId={} name='{}'", apiId, apiName);
                healthCheckService.runScheduledCheck(apiId);
            });
        }
    }

    private boolean isDue(MonitoredApi api, Instant now) {
        if (api.getLastCheckedAt() == null) {
            return true;
        }
        Instant nextCheckAt = api.getLastCheckedAt().plusSeconds(api.getIntervalSeconds());
        return !nextCheckAt.isAfter(now);
    }
}
