package com.apitracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.check")
public record CheckProperties(
        boolean schedulerEnabled,
        long tickMs,
        int poolCoreSize,
        int poolMaxSize,
        int poolQueueCapacity,
        int errorMessageMaxLength,
        int retentionDays,
        boolean retentionEnabled,
        boolean ssrfProtectionEnabled
) {
    public CheckProperties {
        if (tickMs <= 0) {
            tickMs = 5000L;
        }
        if (poolCoreSize <= 0) {
            poolCoreSize = 8;
        }
        if (poolMaxSize <= 0) {
            poolMaxSize = 32;
        }
        if (poolQueueCapacity <= 0) {
            poolQueueCapacity = 200;
        }
        if (errorMessageMaxLength <= 0) {
            errorMessageMaxLength = 1000;
        }
        if (retentionDays <= 0) {
            retentionDays = 30;
        }
    }
}
