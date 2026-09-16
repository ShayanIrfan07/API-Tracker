package com.apitracker.monitor.controller;

import com.apitracker.monitor.dto.ApiUptimeSummaryResponse;
import com.apitracker.monitor.dto.CheckNowResponse;
import com.apitracker.monitor.dto.CheckResultResponse;
import com.apitracker.monitor.dto.CreateMonitoredApiRequest;
import com.apitracker.monitor.dto.MonitoredApiResponse;
import com.apitracker.monitor.dto.UpdateMonitoredApiRequest;
import com.apitracker.monitor.service.HealthCheckService;
import com.apitracker.monitor.service.MonitoredApiService;
import com.apitracker.monitor.service.UptimeSummaryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitored-apis")
@RequiredArgsConstructor
public class MonitoredApiController {

    private final MonitoredApiService monitoredApiService;
    private final HealthCheckService healthCheckService;
    private final UptimeSummaryService uptimeSummaryService;

    @PostMapping
    public ResponseEntity<MonitoredApiResponse> create(@Valid @RequestBody CreateMonitoredApiRequest request) {
        MonitoredApiResponse created = monitoredApiService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<MonitoredApiResponse> findAll() {
        return monitoredApiService.findAll();
    }

    @GetMapping("/{id}")
    public MonitoredApiResponse findById(@PathVariable UUID id) {
        return monitoredApiService.findById(id);
    }

    @PutMapping("/{id}")
    public MonitoredApiResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMonitoredApiRequest request) {
        return monitoredApiService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public MonitoredApiResponse disable(@PathVariable UUID id) {
        return monitoredApiService.disable(id);
    }

    @PostMapping("/{id}/check-now")
    public CheckNowResponse checkNow(@PathVariable UUID id) {
        return healthCheckService.checkNow(id);
    }

    @GetMapping("/{id}/checks")
    public Page<CheckResultResponse> getChecks(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return healthCheckService.getCheckHistory(id, pageable);
    }

    @GetMapping("/{id}/summary")
    public ApiUptimeSummaryResponse getSummary(
            @PathVariable UUID id,
            @RequestParam(name = "hours", defaultValue = "24") int hours) {
        return uptimeSummaryService.summarizeApi(id, hours);
    }
}
