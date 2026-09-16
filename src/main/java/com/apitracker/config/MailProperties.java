package com.apitracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        boolean enabled,
        String provider,
        String from,
        BrevoProperties brevo
) {
    public MailProperties {
        if (!StringUtils.hasText(provider)) {
            provider = "mailpit";
        }
    }

    public boolean isConfigured() {
        if (!enabled || !StringUtils.hasText(from)) {
            return false;
        }
        if (usesBrevo()) {
            return brevo != null
                    && StringUtils.hasText(brevo.apiUrl())
                    && StringUtils.hasText(brevo.apiKey());
        }
        return true;
    }

    public boolean usesBrevo() {
        return "brevo".equalsIgnoreCase(provider);
    }

    public record BrevoProperties(
            String apiUrl,
            String apiKey
    ) {
    }
}
