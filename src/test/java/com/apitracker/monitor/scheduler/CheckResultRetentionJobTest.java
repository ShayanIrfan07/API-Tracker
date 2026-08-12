package com.apitracker.monitor.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.config.CheckProperties;
import com.apitracker.monitor.metrics.MonitoringMetrics;
import com.apitracker.monitor.repository.CheckResultRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckResultRetentionJobTest {

    @Mock
    private CheckProperties checkProperties;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private MonitoringMetrics monitoringMetrics;

    @InjectMocks
    private CheckResultRetentionJob retentionJob;

    @Test
    void skipsWhenDisabled() {
        when(checkProperties.retentionEnabled()).thenReturn(false);

        retentionJob.purgeOldCheckResults();

        verify(checkResultRepository, never()).deleteByCheckedAtBefore(any(Instant.class));
    }

    @Test
    void purgesAndRecordsMetric() {
        when(checkProperties.retentionEnabled()).thenReturn(true);
        when(checkProperties.retentionDays()).thenReturn(30);
        when(checkResultRepository.deleteByCheckedAtBefore(any(Instant.class))).thenReturn(12);

        retentionJob.purgeOldCheckResults();

        verify(monitoringMetrics).recordRetentionDeleted(12);
    }
}
