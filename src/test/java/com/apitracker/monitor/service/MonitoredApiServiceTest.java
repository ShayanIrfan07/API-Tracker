package com.apitracker.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apitracker.exception.ConflictException;
import com.apitracker.exception.ResourceNotFoundException;
import com.apitracker.monitor.dto.CreateMonitoredApiRequest;
import com.apitracker.monitor.dto.MonitoredApiResponse;
import com.apitracker.monitor.dto.UpdateMonitoredApiRequest;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitoredApiServiceTest {

    @Mock
    private MonitoredApiRepository monitoredApiRepository;

    @InjectMocks
    private MonitoredApiService monitoredApiService;

    private UUID apiId;
    private MonitoredApi existing;

    @BeforeEach
    void setUp() {
        apiId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        existing = MonitoredApi.builder()
                .id(apiId)
                .name("Payments API")
                .baseUrl("https://api.example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(3000)
                .intervalSeconds(60)
                .failureThreshold(3)
                .successThreshold(2)
                .ownerEmail("ops@example.com")
                .enabled(true)
                .currentStatus(ApiStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void createMonitoredApi() {
        CreateMonitoredApiRequest request = new CreateMonitoredApiRequest(
                "Payments API",
                "https://api.example.com/",
                "health",
                HttpMethod.GET,
                200,
                3000,
                60,
                null,
                null,
                null,
                "ops@example.com",
                null);

        when(monitoredApiRepository.existsByNameIgnoreCase("Payments API")).thenReturn(false);
        when(monitoredApiRepository.save(any(MonitoredApi.class))).thenAnswer(invocation -> {
            MonitoredApi saved = invocation.getArgument(0);
            saved.setCreatedAt(Instant.parse("2026-08-08T10:00:00Z"));
            saved.setUpdatedAt(Instant.parse("2026-08-08T10:00:00Z"));
            return saved;
        });

        MonitoredApiResponse response = monitoredApiService.create(request);

        ArgumentCaptor<MonitoredApi> captor = ArgumentCaptor.forClass(MonitoredApi.class);
        verify(monitoredApiRepository).save(captor.capture());
        MonitoredApi persisted = captor.getValue();

        assertThat(persisted.getBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(persisted.getPath()).isEqualTo("/health");
        assertThat(persisted.getOwnerEmail()).isEqualTo("ops@example.com");
        assertThat(persisted.getEnabled()).isTrue();
        assertThat(response.name()).isEqualTo("Payments API");
    }

    @Test
    void createRejectsInvalidUrl() {
        CreateMonitoredApiRequest request = new CreateMonitoredApiRequest(
                "Bad URL API",
                "not-a-url",
                "/health",
                HttpMethod.GET,
                200,
                3000,
                60,
                null,
                null,
                null,
                null,
                true);

        when(monitoredApiRepository.existsByNameIgnoreCase("Bad URL API")).thenReturn(false);

        assertThatThrownBy(() -> monitoredApiService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void createRejectsDuplicateName() {
        CreateMonitoredApiRequest request = new CreateMonitoredApiRequest(
                "Payments API",
                "https://api.example.com",
                "/health",
                HttpMethod.GET,
                200,
                3000,
                60,
                3,
                2,
                null,
                null,
                true);

        when(monitoredApiRepository.existsByNameIgnoreCase("Payments API")).thenReturn(true);

        assertThatThrownBy(() -> monitoredApiService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void retrieveApi() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(existing));

        MonitoredApiResponse response = monitoredApiService.findById(apiId);

        assertThat(response.id()).isEqualTo(apiId);
        assertThat(response.ownerEmail()).isEqualTo("ops@example.com");
    }

    @Test
    void retrieveAllApis() {
        when(monitoredApiRepository.findAll()).thenReturn(List.of(existing));

        List<MonitoredApiResponse> responses = monitoredApiService.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(apiId);
    }

    @Test
    void updateApi() {
        UpdateMonitoredApiRequest request = new UpdateMonitoredApiRequest(
                "Payments API v2",
                "https://api.example.com",
                "/v2/health",
                HttpMethod.GET,
                204,
                5000,
                30,
                5,
                3,
                250,
                "team@example.com",
                true);

        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(existing));
        when(monitoredApiRepository.existsByNameIgnoreCaseAndIdNot("Payments API v2", apiId)).thenReturn(false);
        when(monitoredApiRepository.save(any(MonitoredApi.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredApiResponse response = monitoredApiService.update(apiId, request);

        assertThat(response.name()).isEqualTo("Payments API v2");
        assertThat(response.ownerEmail()).isEqualTo("team@example.com");
        assertThat(response.failureThreshold()).isEqualTo(5);
    }

    @Test
    void disableApi() {
        when(monitoredApiRepository.findById(apiId)).thenReturn(Optional.of(existing));
        when(monitoredApiRepository.save(any(MonitoredApi.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredApiResponse response = monitoredApiService.disable(apiId);

        assertThat(response.enabled()).isFalse();
    }

    @Test
    void apiNotFound() {
        UUID missing = UUID.randomUUID();
        when(monitoredApiRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitoredApiService.findById(missing))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
