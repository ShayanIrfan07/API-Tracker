package com.apitracker.monitor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.CheckResult;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class MonitoredApiRepositoryTest {

    @Autowired
    private MonitoredApiRepository monitoredApiRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Test
    void saveAndFindById() {
        MonitoredApi saved = monitoredApiRepository.save(sample("Catalog API", true));

        assertThat(monitoredApiRepository.findById(saved.getId())).isPresent();
        assertThat(monitoredApiRepository.findById(saved.getId()).orElseThrow().getName())
                .isEqualTo("Catalog API");
    }

    @Test
    void findByEnabledTrue() {
        monitoredApiRepository.save(sample("Enabled API", true));
        monitoredApiRepository.save(sample("Disabled API", false));

        List<MonitoredApi> enabled = monitoredApiRepository.findByEnabledTrue();

        assertThat(enabled).extracting(MonitoredApi::getName).containsExactly("Enabled API");
    }

    @Test
    void existsByNameIgnoreCase() {
        monitoredApiRepository.save(sample("Unique API", true));

        assertThat(monitoredApiRepository.existsByNameIgnoreCase("unique api")).isTrue();
        assertThat(monitoredApiRepository.existsByNameIgnoreCase("missing")).isFalse();
    }

    @Test
    void saveAndPageCheckResults() {
        MonitoredApi api = monitoredApiRepository.save(sample("Checked API", true));
        checkResultRepository.save(CheckResult.builder()
                .monitoredApi(api)
                .checkedAt(Instant.parse("2026-08-08T10:00:00Z"))
                .success(true)
                .apiStatus(ApiStatus.UP)
                .timedOut(false)
                .httpStatus(200)
                .latencyMs(12)
                .build());
        checkResultRepository.save(CheckResult.builder()
                .monitoredApi(api)
                .checkedAt(Instant.parse("2026-08-08T10:01:00Z"))
                .success(false)
                .apiStatus(ApiStatus.DOWN)
                .timedOut(false)
                .httpStatus(500)
                .latencyMs(20)
                .errorMessage("Unexpected status code")
                .build());

        var page = checkResultRepository.findByMonitoredApiIdOrderByCheckedAtDesc(
                api.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().getFirst().getSuccess()).isFalse();
        assertThat(page.getContent().get(1).getSuccess()).isTrue();
    }

    private MonitoredApi sample(String name, boolean enabled) {
        return MonitoredApi.builder()
                .id(UUID.randomUUID())
                .name(name)
                .baseUrl("https://example.com")
                .path("/health")
                .httpMethod(HttpMethod.GET)
                .expectedStatusCode(200)
                .timeoutMs(1000)
                .intervalSeconds(60)
                .failureThreshold(3)
                .successThreshold(2)
                .ownerEmail("ops@example.com")
                .enabled(enabled)
                .currentStatus(ApiStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .build();
    }
}
