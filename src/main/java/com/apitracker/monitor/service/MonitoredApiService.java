package com.apitracker.monitor.service;

import com.apitracker.exception.ConflictException;
import com.apitracker.exception.ResourceNotFoundException;
import com.apitracker.monitor.dto.CreateMonitoredApiRequest;
import com.apitracker.monitor.dto.MonitoredApiResponse;
import com.apitracker.monitor.dto.UpdateMonitoredApiRequest;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.MonitoredApi;
import com.apitracker.monitor.repository.MonitoredApiRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class MonitoredApiService {

    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final int DEFAULT_SUCCESS_THRESHOLD = 2;

    private final MonitoredApiRepository monitoredApiRepository;

    public MonitoredApiResponse create(CreateMonitoredApiRequest request) {
        if (monitoredApiRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Monitored API with name already exists: " + request.name());
        }

        MonitoredApi entity = MonitoredApi.builder()
                .id(UUID.randomUUID())
                .name(request.name().trim())
                .baseUrl(normalizeBaseUrl(request.baseUrl()))
                .path(normalizePath(request.path()))
                .httpMethod(request.httpMethod())
                .expectedStatusCode(request.expectedStatusCode())
                .timeoutMs(request.timeoutMs())
                .intervalSeconds(request.intervalSeconds())
                .failureThreshold(request.failureThreshold() == null
                        ? DEFAULT_FAILURE_THRESHOLD
                        : request.failureThreshold())
                .successThreshold(request.successThreshold() == null
                        ? DEFAULT_SUCCESS_THRESHOLD
                        : request.successThreshold())
                .latencyThresholdMs(request.latencyThresholdMs())
                .ownerEmail(normalizeEmail(request.ownerEmail()))
                .enabled(request.enabled() == null || request.enabled())
                .currentStatus(ApiStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .build();

        return toResponse(monitoredApiRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<MonitoredApiResponse> findAll() {
        return monitoredApiRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoredApiResponse findById(UUID id) {
        return toResponse(getEntity(id));
    }

    public MonitoredApiResponse update(UUID id, UpdateMonitoredApiRequest request) {
        MonitoredApi entity = getEntity(id);

        if (monitoredApiRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("Monitored API with name already exists: " + request.name());
        }

        entity.setName(request.name().trim());
        entity.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        entity.setPath(normalizePath(request.path()));
        entity.setHttpMethod(request.httpMethod());
        entity.setExpectedStatusCode(request.expectedStatusCode());
        entity.setTimeoutMs(request.timeoutMs());
        entity.setIntervalSeconds(request.intervalSeconds());
        entity.setFailureThreshold(request.failureThreshold());
        entity.setSuccessThreshold(request.successThreshold());
        entity.setLatencyThresholdMs(request.latencyThresholdMs());
        entity.setOwnerEmail(normalizeEmail(request.ownerEmail()));
        entity.setEnabled(request.enabled());

        MonitoredApi saved = monitoredApiRepository.save(entity);
        monitoredApiRepository.flush();
        return toResponse(saved);
    }

    public MonitoredApiResponse disable(UUID id) {
        MonitoredApi entity = getEntity(id);
        entity.setEnabled(false);
        MonitoredApi saved = monitoredApiRepository.save(entity);
        monitoredApiRepository.flush();
        return toResponse(saved);
    }

    public MonitoredApiResponse toResponse(MonitoredApi entity) {
        return new MonitoredApiResponse(
                entity.getId(),
                entity.getName(),
                entity.getBaseUrl(),
                entity.getPath(),
                entity.getHttpMethod(),
                entity.getExpectedStatusCode(),
                entity.getTimeoutMs(),
                entity.getIntervalSeconds(),
                entity.getFailureThreshold(),
                entity.getSuccessThreshold(),
                entity.getLatencyThresholdMs(),
                entity.getOwnerEmail(),
                entity.getEnabled(),
                entity.getCurrentStatus(),
                entity.getConsecutiveFailures(),
                entity.getConsecutiveSuccesses(),
                entity.getLastCheckedAt(),
                entity.getLastStatusChangeAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private MonitoredApi getEntity(UUID id) {
        return monitoredApiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MonitoredApi", id));
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim();
    }
}
