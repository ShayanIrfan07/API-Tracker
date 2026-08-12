package com.apitracker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    @Test
    void allowsUpToCapacityThenRejects() {
        RateLimitProperties properties = new RateLimitProperties(true, 3, 3, 3);
        RateLimitService service = new RateLimitService(properties);

        assertThat(service.tryConsume("api:test", 3)).isTrue();
        assertThat(service.tryConsume("api:test", 3)).isTrue();
        assertThat(service.tryConsume("api:test", 3)).isTrue();
        assertThat(service.tryConsume("api:test", 3)).isFalse();
    }

    @Test
    void disabledAlwaysAllows() {
        RateLimitProperties properties = new RateLimitProperties(false, 1, 1, 1);
        RateLimitService service = new RateLimitService(properties);

        assertThat(service.tryConsume("api:test", 1)).isTrue();
        assertThat(service.tryConsume("api:test", 1)).isTrue();
    }

    @Test
    void separateBucketsAreIndependent() {
        RateLimitProperties properties = new RateLimitProperties(true, 1, 1, 1);
        RateLimitService service = new RateLimitService(properties);

        assertThat(service.tryConsume("api:a", 1)).isTrue();
        assertThat(service.tryConsume("api:b", 1)).isTrue();
        assertThat(service.tryConsume("api:a", 1)).isFalse();
    }
}
