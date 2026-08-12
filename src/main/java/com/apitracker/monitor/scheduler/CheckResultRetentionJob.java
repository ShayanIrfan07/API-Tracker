package com.apitracker.monitor.scheduler;

import com.apitracker.config.CheckProperties;
import com.apitracker.monitor.metrics.MonitoringMetrics;
import com.apitracker.monitor.repository.CheckResultRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckResultRetentionJob {

    private final CheckProperties checkProperties;
    private final CheckResultRepository checkResultRepository;
    private final MonitoringMetrics monitoringMetrics;

    @Scheduled(cron = "${app.check.retention-cron:0 30 2 * * *}")
    @Transactional
    public void purgeOldCheckResults() {
        if (!checkProperties.retentionEnabled()) {
            return;
        }

        Instant cutoff = Instant.now().minus(checkProperties.retentionDays(), ChronoUnit.DAYS);
        int deleted = checkResultRepository.deleteByCheckedAtBefore(cutoff);
        if (deleted > 0) {
            monitoringMetrics.recordRetentionDeleted(deleted);
            log.info("Purged {} check_result row(s) older than {} days (cutoff={})",
                    deleted, checkProperties.retentionDays(), cutoff);
        } else {
            log.debug("Check-result retention ran; nothing older than {} to delete", cutoff);
        }
    }
}
