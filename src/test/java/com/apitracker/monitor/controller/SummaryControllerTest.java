package com.apitracker.monitor.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apitracker.auth.JwtTokenService;
import com.apitracker.config.SecurityConfig;
import com.apitracker.exception.GlobalExceptionHandler;
import com.apitracker.monitor.dto.FleetSummaryResponse;
import com.apitracker.monitor.service.UptimeSummaryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SummaryController.class)
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
class SummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UptimeSummaryService uptimeSummaryService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    void fleetSummaryReturns200() throws Exception {
        Instant end = Instant.parse("2026-08-08T12:00:00Z");
        when(uptimeSummaryService.summarizeFleet(24)).thenReturn(new FleetSummaryResponse(
                24,
                end.minusSeconds(86_400),
                end,
                3,
                3,
                2,
                0,
                1,
                0,
                100,
                95,
                99.5,
                42.0,
                1,
                4,
                120.0,
                List.of()));

        mockMvc.perform(get("/api/v1/summary").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApis").value(3))
                .andExpect(jsonPath("$.upCount").value(2))
                .andExpect(jsonPath("$.downCount").value(1))
                .andExpect(jsonPath("$.openIncidentCount").value(1))
                .andExpect(jsonPath("$.mttrSeconds").value(120.0))
                .andExpect(jsonPath("$.fleetUptimePercent").value(99.5));
    }
}
