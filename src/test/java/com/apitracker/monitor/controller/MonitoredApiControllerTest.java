package com.apitracker.monitor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apitracker.auth.JwtTokenService;
import com.apitracker.config.SecurityConfig;
import com.apitracker.exception.GlobalExceptionHandler;
import com.apitracker.exception.ResourceNotFoundException;
import com.apitracker.monitor.dto.CheckResultResponse;
import com.apitracker.monitor.dto.CreateMonitoredApiRequest;
import com.apitracker.monitor.dto.MonitoredApiResponse;
import com.apitracker.monitor.dto.UpdateMonitoredApiRequest;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.service.HealthCheckService;
import com.apitracker.monitor.service.MonitoredApiService;
import com.apitracker.monitor.service.UptimeSummaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MonitoredApiController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@TestPropertySource(properties = {
        "app.security.api-key=",
        "app.security.require-api-key=false",
        "app.security.admin-username=admin",
        "app.security.admin-password=admin",
        "app.security.jwt-secret=test-secret-key-which-is-long-enough-01",
        "app.security.jwt-expiration-ms=3600000"
})
class MonitoredApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MonitoredApiService monitoredApiService;

    @MockitoBean
    private HealthCheckService healthCheckService;

    @MockitoBean
    private UptimeSummaryService uptimeSummaryService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        CreateMonitoredApiRequest request = new CreateMonitoredApiRequest(
                "Orders API",
                "https://orders.example.com",
                "/health",
                HttpMethod.GET,
                200,
                2000,
                30,
                3,
                2,
                null,
                "ops@example.com",
                true);

        when(monitoredApiService.create(any(CreateMonitoredApiRequest.class)))
                .thenReturn(sampleResponse(id, "/health", true));

        mockMvc.perform(post("/api/v1/monitored-apis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Orders API"))
                .andExpect(jsonPath("$.currentStatus").value("UNKNOWN"));
    }

    @Test
    void getAllReturns200() throws Exception {
        when(monitoredApiService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/monitored-apis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getByIdReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(monitoredApiService.findById(id)).thenReturn(sampleResponse(id, "/health", true));

        mockMvc.perform(get("/api/v1/monitored-apis/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getByIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(monitoredApiService.findById(id)).thenThrow(new ResourceNotFoundException("MonitoredApi", id));

        mockMvc.perform(get("/api/v1/monitored-apis/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void validationFailureReturns400() throws Exception {
        String invalidJson = """
                {
                  "name": "",
                  "baseUrl": "",
                  "httpMethod": "GET",
                  "expectedStatusCode": 99,
                  "timeoutMs": -1,
                  "intervalSeconds": 0
                }
                """;

        mockMvc.perform(post("/api/v1/monitored-apis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void updateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateMonitoredApiRequest request = new UpdateMonitoredApiRequest(
                "Orders API",
                "https://orders.example.com",
                "/ready",
                HttpMethod.GET,
                200,
                2500,
                45,
                3,
                2,
                null,
                "ops@example.com",
                true);

        when(monitoredApiService.update(eq(id), any(UpdateMonitoredApiRequest.class)))
                .thenReturn(sampleResponse(id, "/ready", true));

        mockMvc.perform(put("/api/v1/monitored-apis/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/ready"));
    }

    @Test
    void checkNowReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(healthCheckService.checkNow(id)).thenReturn(sampleResponse(id, "/health", true));

        mockMvc.perform(post("/api/v1/monitored-apis/{id}/check-now", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getChecksReturnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        when(healthCheckService.getCheckHistory(eq(id), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        new CheckResultResponse(1L, id, now, true, 200, 15, null))));

        mockMvc.perform(get("/api/v1/monitored-apis/{id}/checks", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].success").value(true))
                .andExpect(jsonPath("$.content[0].httpStatus").value(200));
    }

    private MonitoredApiResponse sampleResponse(UUID id, String path, boolean enabled) {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        return new MonitoredApiResponse(
                id,
                "Orders API",
                "https://orders.example.com",
                path,
                HttpMethod.GET,
                200,
                2000,
                30,
                3,
                2,
                null,
                "ops@example.com",
                enabled,
                ApiStatus.UNKNOWN,
                0,
                0,
                null,
                null,
                now,
                now);
    }
}
