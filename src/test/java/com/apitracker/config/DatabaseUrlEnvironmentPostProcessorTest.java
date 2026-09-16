package com.apitracker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apitracker.config.DatabaseUrlEnvironmentPostProcessor.ParsedDatabaseUrl;
import org.junit.jupiter.api.Test;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void parsesPostgresUrl() {
        ParsedDatabaseUrl parsed = DatabaseUrlEnvironmentPostProcessor.parseDatabaseUrl(
                "postgresql://api:s3cret@dpg-example-a.oregon-postgres.render.com:5432/api_tracker");

        assertThat(parsed.jdbcUrl())
                .isEqualTo("jdbc:postgresql://dpg-example-a.oregon-postgres.render.com:5432/api_tracker");
        assertThat(parsed.username()).isEqualTo("api");
        assertThat(parsed.password()).isEqualTo("s3cret");
    }

    @Test
    void parsesPostgresSchemeAlias() {
        ParsedDatabaseUrl parsed = DatabaseUrlEnvironmentPostProcessor.parseDatabaseUrl(
                "postgres://user:pass@localhost:5432/api_tracker");

        assertThat(parsed.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/api_tracker");
        assertThat(parsed.username()).isEqualTo("user");
        assertThat(parsed.password()).isEqualTo("pass");
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThatThrownBy(() -> DatabaseUrlEnvironmentPostProcessor.parseDatabaseUrl("mysql://localhost/db"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesSupabasePoolerStyleUrl() {
        ParsedDatabaseUrl parsed = DatabaseUrlEnvironmentPostProcessor.parseDatabaseUrl(
                "postgresql://postgres.user:secret@aws-0-ap-south-1.pooler.supabase.com:6543/postgres");

        assertThat(parsed.jdbcUrl())
                .isEqualTo("jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres");
        assertThat(parsed.username()).isEqualTo("postgres.user");
        assertThat(parsed.password()).isEqualTo("secret");
    }

    @Test
    void parsesRenderManagedPostgresUrl() {
        ParsedDatabaseUrl parsed = DatabaseUrlEnvironmentPostProcessor.parseDatabaseUrl(
                "postgresql://api_tracker_user:pass@dpg-example-a.oregon-postgres.render.com/api_tracker");

        assertThat(parsed.jdbcUrl())
                .isEqualTo("jdbc:postgresql://dpg-example-a.oregon-postgres.render.com:5432/api_tracker");
        assertThat(parsed.username()).isEqualTo("api_tracker_user");
        assertThat(parsed.password()).isEqualTo("pass");
    }
}
