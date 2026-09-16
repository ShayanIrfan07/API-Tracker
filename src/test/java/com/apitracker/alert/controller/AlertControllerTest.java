package com.apitracker.alert.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apitracker.alert.dto.AlertResponse;
import com.apitracker.alert.entity.AlertStatus;
import com.apitracker.alert.service.AlertService;
import com.apitracker.auth.JwtTokenService;
import com.apitracker.config.SecurityConfig;
import com.apitracker.exception.GlobalExceptionHandler;
import com.apitracker.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AlertController.class)
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
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertService alertService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    void findAllReturns200() throws Exception {
        UUID alertId = UUID.randomUUID();
        UUID apiId = UUID.randomUUID();
        when(alertService.findAll(null)).thenReturn(List.of(new AlertResponse(
                alertId,
                apiId,
                "Payments API",
                AlertStatus.OPEN,
                Instant.parse("2026-08-08T10:00:00Z"),
                null,
                null,
                null,
                "[API Tracker] Payments API is DOWN",
                "recent failures",
                "Connection refused",
                null)));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alertId.toString()))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void findByIdReturns404() throws Exception {
        UUID alertId = UUID.randomUUID();
        when(alertService.findById(alertId)).thenThrow(new ResourceNotFoundException("Alert", alertId));

        mockMvc.perform(get("/api/v1/alerts/{id}", alertId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
