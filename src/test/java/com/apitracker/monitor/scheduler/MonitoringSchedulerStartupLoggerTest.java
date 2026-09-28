package com.apitracker.monitor.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MonitoringSchedulerStartupLoggerTest {

    @Autowired
    private MonitoringSchedulerStartupLogger startupLogger;

    @Test
    void startupLoggerBeanIsRegistered() {
        assertThatCode(() -> startupLogger.logSchedulerState()).doesNotThrowAnyException();
    }
}
