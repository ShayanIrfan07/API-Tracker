package com.apitracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        boolean enabled,
        String from
) {
    public boolean isConfigured() {
        return enabled && from != null && !from.isBlank();
    }
}
