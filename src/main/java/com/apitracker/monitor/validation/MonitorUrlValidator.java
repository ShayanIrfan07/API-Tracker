package com.apitracker.monitor.validation;

import java.net.URI;
import org.springframework.util.StringUtils;

/**
 * Validates monitored endpoint URLs before persistence.
 * Format checks only — SSRF restrictions are applied separately (Phase 11).
 */
public final class MonitorUrlValidator {

    private MonitorUrlValidator() {
    }

    public static void validate(String normalizedBaseUrl, String normalizedPath) {
        if (!StringUtils.hasText(normalizedBaseUrl)) {
            throw new IllegalArgumentException("baseUrl is required");
        }

        URI uri;
        try {
            uri = URI.create(normalizedBaseUrl + normalizedPath);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("baseUrl must use http or https");
        }

        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("baseUrl must include a valid host");
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URL must not include embedded credentials");
        }
    }
}
