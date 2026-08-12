package com.apitracker.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Adapts PaaS env vars (e.g. Render):
 * - {@code PORT} already mapped via application.yml
 * - {@code DATABASE_URL} (postgres://...) → JDBC datasource settings when {@code DB_URL} is unset
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = new HashMap<>();

        String explicitJdbc = firstNonBlank(
                environment.getProperty("DB_URL"),
                environment.getProperty("spring.datasource.url"));
        String databaseUrl = environment.getProperty("DATABASE_URL");

        if (!StringUtils.hasText(explicitJdbc) && StringUtils.hasText(databaseUrl)) {
            try {
                ParsedDatabaseUrl parsed = parseDatabaseUrl(databaseUrl);
                props.put("spring.datasource.url", parsed.jdbcUrl());
                if (!StringUtils.hasText(environment.getProperty("DB_USERNAME"))
                        && !StringUtils.hasText(environment.getProperty("spring.datasource.username"))
                        && StringUtils.hasText(parsed.username())) {
                    props.put("spring.datasource.username", parsed.username());
                }
                if (!StringUtils.hasText(environment.getProperty("DB_PASSWORD"))
                        && !StringUtils.hasText(environment.getProperty("spring.datasource.password"))
                        && parsed.password() != null) {
                    props.put("spring.datasource.password", parsed.password());
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Invalid DATABASE_URL for Postgres binding", ex);
            }
        }

        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrlAdapter", props));
        }
    }

    static ParsedDatabaseUrl parseDatabaseUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("DATABASE_URL is blank");
        }

        String normalized = raw.trim();
        if (normalized.startsWith("postgres://")) {
            normalized = "postgresql://" + normalized.substring("postgres://".length());
        }
        if (normalized.startsWith("jdbc:postgresql://")) {
            return new ParsedDatabaseUrl(normalized, null, null);
        }
        if (!normalized.startsWith("postgresql://")) {
            throw new IllegalArgumentException("Unsupported DATABASE_URL scheme: " + raw);
        }

        try {
            URI uri = new URI(normalized);
            String user = null;
            String password = null;
            if (StringUtils.hasText(uri.getUserInfo())) {
                String[] parts = uri.getUserInfo().split(":", 2);
                user = parts[0];
                password = parts.length > 1 ? parts[1] : "";
            }

            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/postgres" : uri.getPath();
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + path;
            if (StringUtils.hasText(uri.getQuery())) {
                jdbc = jdbc + "?" + uri.getQuery();
            }
            return new ParsedDatabaseUrl(jdbc, user, password);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Could not parse DATABASE_URL", ex);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    record ParsedDatabaseUrl(String jdbcUrl, String username, String password) {
    }
}
