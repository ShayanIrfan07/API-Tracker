package com.apitracker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailPropertiesTest {

    @Test
    void configuredWhenEnabledAndFromPresent() {
        MailProperties props = new MailProperties(true, "alerts@example.com");
        assertThat(props.isConfigured()).isTrue();
    }

    @Test
    void notConfiguredWhenDisabled() {
        MailProperties props = new MailProperties(false, "alerts@example.com");
        assertThat(props.isConfigured()).isFalse();
    }

    @Test
    void notConfiguredWhenFromMissing() {
        MailProperties props = new MailProperties(true, "  ");
        assertThat(props.isConfigured()).isFalse();
    }
}
