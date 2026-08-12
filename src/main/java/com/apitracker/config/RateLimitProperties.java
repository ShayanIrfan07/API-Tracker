package com.apitracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int apiPermitsPerMinute,
        int loginPermitsPerMinute,
        int checkNowPermitsPerMinute
) {
    public RateLimitProperties {
        if (apiPermitsPerMinute <= 0) {
            apiPermitsPerMinute = 120;
        }
        if (loginPermitsPerMinute <= 0) {
            loginPermitsPerMinute = 20;
        }
        if (checkNowPermitsPerMinute <= 0) {
            checkNowPermitsPerMinute = 30;
        }
    }
}
