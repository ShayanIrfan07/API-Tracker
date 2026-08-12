package com.apitracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String apiKey,
        boolean requireApiKey,
        String adminUsername,
        String adminPassword,
        String jwtSecret,
        long jwtExpirationMs
) {
    public SecurityProperties {
        if (adminUsername == null || adminUsername.isBlank()) {
            adminUsername = "admin";
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            adminPassword = "admin";
        }
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = "change-me-to-a-long-random-secret-key-32b";
        }
        if (jwtExpirationMs <= 0) {
            jwtExpirationMs = 86_400_000L;
        }
    }
}
