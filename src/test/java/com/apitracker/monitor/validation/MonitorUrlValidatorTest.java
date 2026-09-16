package com.apitracker.monitor.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MonitorUrlValidatorTest {

    @Test
    void acceptsHttpsUrlWithPath() {
        assertThatCode(() -> MonitorUrlValidator.validate("https://example.com", "/status/200"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsHttpUrlWithDefaultPath() {
        assertThatCode(() -> MonitorUrlValidator.validate("http://api.example.com", "/"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingScheme() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("example.com", "/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("ftp://example.com", "/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void rejectsMissingHost() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("https://", "/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid host");
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("https://user:pass@example.com", "/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentials");
    }
}
