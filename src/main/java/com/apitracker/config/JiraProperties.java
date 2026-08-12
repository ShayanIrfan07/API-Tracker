package com.apitracker.config;

import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jira")
public record JiraProperties(
        boolean enabled,
        String baseUrl,
        String username,
        String apiToken,
        String projectKey,
        String issueType,
        String priority
) {
    public JiraProperties {
        if (issueType == null || issueType.isBlank()) {
            issueType = "Bug";
        }
        if (priority == null || priority.isBlank()) {
            priority = "High";
        }
    }

    public boolean isConfigured() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && username != null && !username.isBlank()
                && apiToken != null && !apiToken.isBlank()
                && projectKey != null && !projectKey.isBlank();
    }

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public Optional<String> browseUrl(String issueKey) {
        if (issueKey == null || issueKey.isBlank() || !isConfigured()) {
            return Optional.empty();
        }
        return Optional.of(normalizedBaseUrl() + "/browse/" + issueKey);
    }
}
