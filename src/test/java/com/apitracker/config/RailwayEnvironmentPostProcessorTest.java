package com.apitracker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apitracker.config.RailwayEnvironmentPostProcessor.ParsedDatabaseUrl;
import org.junit.jupiter.api.Test;

class RailwayEnvironmentPostProcessorTest {

    @Test
    void parsesPostgresUrl() {
        ParsedDatabaseUrl parsed = RailwayEnvironmentPostProcessor.parseDatabaseUrl(
                "postgresql://api:s3cret@monorail.proxy.rlwy.net:1234/railway");

        assertThat(parsed.jdbcUrl()).isEqualTo("jdbc:postgresql://monorail.proxy.rlwy.net:1234/railway");
        assertThat(parsed.username()).isEqualTo("api");
        assertThat(parsed.password()).isEqualTo("s3cret");
    }

    @Test
    void parsesPostgresSchemeAlias() {
        ParsedDatabaseUrl parsed = RailwayEnvironmentPostProcessor.parseDatabaseUrl(
                "postgres://user:pass@localhost:5432/api_tracker");

        assertThat(parsed.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/api_tracker");
        assertThat(parsed.username()).isEqualTo("user");
        assertThat(parsed.password()).isEqualTo("pass");
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThatThrownBy(() -> RailwayEnvironmentPostProcessor.parseDatabaseUrl("mysql://localhost/db"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
