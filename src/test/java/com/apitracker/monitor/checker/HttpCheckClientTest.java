package com.apitracker.monitor.checker;

import static org.assertj.core.api.Assertions.assertThat;

import com.apitracker.config.CheckProperties;
import com.apitracker.monitor.entity.ApiStatus;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpCheckClientTest {

    private HttpServer server;
    private int port;
    private HttpCheckClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new HttpCheckClient(new CheckProperties(
                false,
                60_000L,
                1,
                1,
                10,
                1000,
                30,
                true,
                false));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsSuccessForMatchingStatus() throws IOException {
        port = startServer(exchange -> {
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        HttpCheckOutcome outcome = client.check(sampleApi(HttpMethod.GET, "/ok", 200, 3000));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(outcome.latencyMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(outcome.errorMessage()).isNull();
    }

    @Test
    void returnsFailureForUnexpectedStatus() throws IOException {
        port = startServer(exchange -> exchange.sendResponseHeaders(503, -1));

        HttpCheckOutcome outcome = client.check(sampleApi(HttpMethod.GET, "/fail", 200, 3000));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isEqualTo(503);
        assertThat(outcome.errorMessage()).contains("Unexpected status code");
    }

    @Test
    void returnsFailureOnTimeout() throws IOException {
        port = startServer(exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
        });

        HttpCheckOutcome outcome = client.check(sampleApi(HttpMethod.GET, "/slow", 200, 100));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.timedOut()).isTrue();
        assertThat(outcome.errorMessage()).isNotBlank();
    }

    @Test
    void returnsFailureOnConnectionRefused() {
        MonitoredApi api = sampleApi(HttpMethod.GET, "/missing", 200, 1000);
        api.setBaseUrl("http://127.0.0.1:1");

        HttpCheckOutcome outcome = client.check(api);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.errorMessage()).isNotBlank();
    }

    @Test
    void blocksLoopbackTargetWhenSsrfProtectionEnabled() {
        HttpCheckClient protectedClient = new HttpCheckClient(new CheckProperties(
                false,
                60_000L,
                1,
                1,
                10,
                1000,
                30,
                true,
                true));
        MonitoredApi api = sampleApi(HttpMethod.GET, "/health", 200, 1000);

        HttpCheckOutcome outcome = protectedClient.check(api);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorMessage()).contains("disallowed");
    }

    @Test
    void supportsPostMethod() throws IOException {
        port = startServer(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            exchange.sendResponseHeaders(201, -1);
        });

        HttpCheckOutcome outcome = client.check(sampleApi(HttpMethod.POST, "/create", 201, 3000));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.httpStatus()).isEqualTo(201);
    }

    private int startServer(com.sun.net.httpserver.HttpHandler handler) {
        server.createContext("/", handler);
        server.start();
        return server.getAddress().getPort();
    }

    private MonitoredApi sampleApi(HttpMethod method, String path, int expectedStatus, int timeoutMs) {
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        return MonitoredApi.builder()
                .id(UUID.randomUUID())
                .name("Test API")
                .baseUrl("http://127.0.0.1:" + port)
                .path(path)
                .httpMethod(method)
                .expectedStatusCode(expectedStatus)
                .timeoutMs(timeoutMs)
                .intervalSeconds(60)
                .failureThreshold(3)
                .successThreshold(2)
                .enabled(true)
                .currentStatus(ApiStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
