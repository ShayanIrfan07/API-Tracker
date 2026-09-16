package com.apitracker.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.alert.repository.AlertRepository;
import com.apitracker.monitor.dto.ApiUptimeSummaryResponse;
import com.apitracker.monitor.dto.FleetSummaryResponse;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.repository.ApiCheckAggregate;
import com.apitracker.monitor.repository.CheckAggregate;
import com.apitracker.monitor.repository.CheckResultRepository;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UptimeSummaryServiceTest {

    @Mock
    private MonitoredApiRepository monitoredApiRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private UptimeSummaryService uptimeSummaryService;

    private UUID apiId;
    private MonitoredApi api;

    @BeforeEach
    void setUp() {
        apiId = UUID.randomUUID();
        api = MonitoredApi.builder()
                .id(apiId)
                .name("Payments")
                .baseUrl("https://example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(3000)
                .intervalSeconds(60)
                .enabled(true)
                .currentStatus(ApiStatus.UP)
                .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
    }

    @Test
    void summarizeApiComputesUptimeAndLatency() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(checkResultRepository.aggregateForApiSince(eq(apiId), any()))
                .thenReturn(new CheckAggregate(10L, 8L, 120.4d, 200, 50));

        ApiUptimeSummaryResponse summary = uptimeSummaryService.summarizeApi(apiId, 24);

        assertThat(summary.totalChecks()).isEqualTo(10);
        assertThat(summary.successfulChecks()).isEqualTo(8);
        assertThat(summary.failedChecks()).isEqualTo(2);
        assertThat(summary.uptimePercent()).isEqualTo(80.0);
        assertThat(summary.avgLatencyMs()).isEqualTo(120.4);
        assertThat(summary.maxLatencyMs()).isEqualTo(200);
        assertThat(summary.minLatencyMs()).isEqualTo(50);
        assertThat(summary.windowHours()).isEqualTo(24);
    }

    @Test
    void summarizeApiClampsHoursAndHandlesEmpty() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(api));
        when(checkResultRepository.aggregateForApiSince(eq(apiId), any()))
                .thenReturn(new CheckAggregate(0L, 0L, null, null, null));

        ApiUptimeSummaryResponse summary = uptimeSummaryService.summarizeApi(apiId, 999);

        assertThat(summary.windowHours()).isEqualTo(168);
        assertThat(summary.totalChecks()).isZero();
        assertThat(summary.uptimePercent()).isNull();
        assertThat(summary.avgLatencyMs()).isNull();
    }

    @Test
    void summarizeFleetAggregatesAcrossApis() {
        UUID otherId = UUID.randomUUID();
        MonitoredApi downApi = MonitoredApi.builder()
                .id(otherId)
                .name("Billing")
                .baseUrl("https://billing.example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(3000)
                .intervalSeconds(60)
                .enabled(true)
                .currentStatus(ApiStatus.DOWN)
                .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();

        when(monitoredApiRepository.findAll()).thenReturn(List.of(api, downApi));
        when(checkResultRepository.aggregateAllSince(any())).thenReturn(List.of(
                new ApiCheckAggregate(apiId, 10L, 10L, 100.0d, 150, 80),
                new ApiCheckAggregate(otherId, 10L, 5L, 200.0d, 400, 100)
        ));
        when(alertRepository.countByStatus(AlertStatus.OPEN)).thenReturn(1L);
        when(alertRepository.countByStatusAndResolvedAtGreaterThanEqual(eq(AlertStatus.RESOLVED), any()))
                .thenReturn(2L);
        when(alertRepository.averageDurationSecondsSince(any())).thenReturn(180.0);

        FleetSummaryResponse fleet = uptimeSummaryService.summarizeFleet(24);

        assertThat(fleet.totalApis()).isEqualTo(2);
        assertThat(fleet.upCount()).isEqualTo(1);
        assertThat(fleet.downCount()).isEqualTo(1);
        assertThat(fleet.totalChecks()).isEqualTo(20);
        assertThat(fleet.successfulChecks()).isEqualTo(15);
        assertThat(fleet.fleetUptimePercent()).isEqualTo(75.0);
        assertThat(fleet.openIncidentCount()).isEqualTo(1);
        assertThat(fleet.resolvedIncidentCount()).isEqualTo(2);
        assertThat(fleet.mttrSeconds()).isEqualTo(180.0);
        assertThat(fleet.apis()).hasSize(2);
    }
}
