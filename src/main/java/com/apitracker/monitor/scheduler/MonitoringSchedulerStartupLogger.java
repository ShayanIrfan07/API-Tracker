package com.apitracker.monitor.scheduler;

import com.apitracker.config.CheckProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringSchedulerStartupLogger {

    private final CheckProperties checkProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void logSchedulerState() {
        if (checkProperties.schedulerEnabled()) {
            log.info(
                    "Monitoring scheduler started (CHECK_SCHEDULER_ENABLED=true, tick-ms={})",
                    checkProperties.tickMs());
        } else {
            log.info("Monitoring scheduler disabled (CHECK_SCHEDULER_ENABLED=false)");
        }
    }
}
