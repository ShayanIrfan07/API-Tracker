package com.apitracker.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apitracker.config.CheckProperties;
import com.apitracker.config.JiraProperties;
import com.apitracker.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the application boots in a production-like configuration with optional
 * integrations disabled, which matches the default Render deployment profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "MAIL_ENABLED=false",
        "JIRA_ENABLED=false",
        "CHECK_SCHEDULER_ENABLED=false",
        "CHECK_RETENTION_ENABLED=false",
        "REQUIRE_API_KEY=false"
})
class ApiTrackerDeploymentSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private JiraProperties jiraProperties;

    @Autowired
    private CheckProperties checkProperties;

    @Test
    void contextLoadsWithIntegrationsDisabled() {
        assertThat(mailProperties.enabled()).isFalse();
        assertThat(mailProperties.isConfigured()).isFalse();
        assertThat(jiraProperties.enabled()).isFalse();
        assertThat(jiraProperties.isConfigured()).isFalse();
        assertThat(checkProperties.schedulerEnabled()).isFalse();
        assertThat(checkProperties.retentionEnabled()).isFalse();
    }

    @Test
    void actuatorHealthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
