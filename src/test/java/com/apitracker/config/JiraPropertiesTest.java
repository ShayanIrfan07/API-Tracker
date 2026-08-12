package com.apitracker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JiraPropertiesTest {

    @Test
    void browseUrlUsesNormalizedBase() {
        JiraProperties props = new JiraProperties(
                true,
                "https://example.atlassian.net/",
                "user@example.com",
                "token",
                "OPS",
                "Bug",
                "High");

        assertThat(props.normalizedBaseUrl()).isEqualTo("https://example.atlassian.net");
        assertThat(props.browseUrl("OPS-12")).contains("https://example.atlassian.net/browse/OPS-12");
    }

    @Test
    void browseUrlEmptyWhenDisabledOrBlankKey() {
        JiraProperties props = new JiraProperties(
                false,
                "https://example.atlassian.net",
                "user@example.com",
                "token",
                "OPS",
                "Bug",
                "High");

        assertThat(props.browseUrl("OPS-12")).isEmpty();
        assertThat(props.isConfigured()).isFalse();
    }
}
