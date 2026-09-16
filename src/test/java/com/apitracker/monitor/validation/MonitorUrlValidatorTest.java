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

    @Test
    void rejectsLocalhostHostname() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("http://localhost", "/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsLoopbackIpLiteral() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("http://127.0.0.1", "/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed");
    }

    @Test
    void rejectsPrivateNetworkIpLiteral() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("http://192.168.0.10", "/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed");
    }

    @Test
    void rejectsLinkLocalMetadataIp() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("http://169.254.169.254", "/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed");
    }

    @Test
    void rejectsMetadataHostname() {
        assertThatThrownBy(() -> MonitorUrlValidator.validate("http://metadata.google.internal", "/computeMetadata/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void skipsSsrfChecksWhenDisabled() {
        assertThatCode(() -> MonitorUrlValidator.validate("http://127.0.0.1", "/health", false))
                .doesNotThrowAnyException();
    }
}
