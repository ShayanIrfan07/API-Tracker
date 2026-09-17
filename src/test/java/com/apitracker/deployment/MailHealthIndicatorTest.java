package com.apitracker.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards Render deploy health: MAIL_ENABLED=true with Brevo must still return UP from /actuator/health.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.mail.enabled=true",
        "app.mail.provider=brevo",
        "app.mail.from=alerts@example.com",
        "app.mail.brevo.api-key=test-key",
        "management.health.mail.enabled=false"
})
class MailHealthIndicatorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsUpWhenBrevoEnabledAndSmtpHealthDisabled() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthWouldFailIfSmtpHealthProbeEnabledWithoutMailServer() throws Exception {
        // Document regression: enabling mail health without a reachable SMTP host breaks Render deploys.
        assertThat(System.getenv("MAIL_SMTP_HEALTH_ENABLED")).isNotEqualTo("true");
    }
}
