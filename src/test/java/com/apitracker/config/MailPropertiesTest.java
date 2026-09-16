package com.apitracker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailPropertiesTest {

    @Test
    void configuredWhenEnabledAndFromPresentForMailpit() {
        MailProperties props = new MailProperties(true, "mailpit", "alerts@example.com", null);
        assertThat(props.isConfigured()).isTrue();
        assertThat(props.usesBrevo()).isFalse();
    }

    @Test
    void notConfiguredWhenDisabled() {
        MailProperties props = new MailProperties(false, "mailpit", "alerts@example.com", null);
        assertThat(props.isConfigured()).isFalse();
    }

    @Test
    void notConfiguredWhenFromMissing() {
        MailProperties props = new MailProperties(true, "mailpit", "  ", null);
        assertThat(props.isConfigured()).isFalse();
    }

    @Test
    void defaultsProviderToMailpit() {
        MailProperties props = new MailProperties(true, null, "alerts@example.com", null);
        assertThat(props.provider()).isEqualTo("mailpit");
    }

    @Test
    void configuredWhenBrevoProviderHasApiCredentials() {
        MailProperties props = new MailProperties(
                true,
                "brevo",
                "alerts@example.com",
                new MailProperties.BrevoProperties(
                        "https://api.brevo.com/v3/smtp/email",
                        "test-api-key"));
        assertThat(props.isConfigured()).isTrue();
        assertThat(props.usesBrevo()).isTrue();
    }

    @Test
    void notConfiguredWhenBrevoProviderMissingApiKey() {
        MailProperties props = new MailProperties(
                true,
                "brevo",
                "alerts@example.com",
                new MailProperties.BrevoProperties(
                        "https://api.brevo.com/v3/smtp/email",
                        ""));
        assertThat(props.isConfigured()).isFalse();
    }
}
