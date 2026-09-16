package com.apitracker.monitor.checker;

import com.apitracker.config.CheckProperties;
import com.apitracker.monitor.entity.HttpMethod;
import com.apitracker.monitor.entity.MonitoredApi;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class HttpCheckClient {

    private final CheckProperties checkProperties;

    public HttpCheckOutcome check(MonitoredApi api) {
        String url = buildUrl(api.getBaseUrl(), api.getPath());
        Instant started = Instant.now();

        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(api.getTimeoutMs());
            requestFactory.setReadTimeout(api.getTimeoutMs());

            RestClient restClient = RestClient.builder()
                    .requestFactory(requestFactory)
                    .build();

            var response = restClient
                    .method(toSpringMethod(api.getHttpMethod()))
                    .uri(URI.create(url))
                    .retrieve()
                    .toBodilessEntity();

            int latencyMs = elapsedMillis(started);
            int statusCode = response.getStatusCode().value();
            if (statusCode == api.getExpectedStatusCode()) {
                return HttpCheckOutcome.ok(statusCode, latencyMs);
            }
            return HttpCheckOutcome.failed(
                    statusCode,
                    latencyMs,
                    "Unexpected status code: expected " + api.getExpectedStatusCode() + " but got " + statusCode);
        } catch (RestClientResponseException ex) {
            int latencyMs = elapsedMillis(started);
            int statusCode = ex.getStatusCode().value();
            if (statusCode == api.getExpectedStatusCode()) {
                return HttpCheckOutcome.ok(statusCode, latencyMs);
            }
            return HttpCheckOutcome.failed(
                    statusCode,
                    latencyMs,
                    truncate("Unexpected status code: expected "
                            + api.getExpectedStatusCode()
                            + " but got "
                            + statusCode));
        } catch (RestClientException | IllegalArgumentException ex) {
            int latencyMs = elapsedMillis(started);
            log.debug("HTTP check failed for apiId={} url={}: {}", api.getId(), url, ex.getMessage());
            return HttpCheckOutcome.failed(null, latencyMs, truncate(ex.getMessage()), isTimeout(ex));
        } catch (Exception ex) {
            int latencyMs = elapsedMillis(started);
            log.warn("Unexpected error checking apiId={} url={}", api.getId(), url, ex);
            return HttpCheckOutcome.failed(null, latencyMs, truncate(ex.getMessage()), isTimeout(ex));
        }
    }

    private org.springframework.http.HttpMethod toSpringMethod(HttpMethod method) {
        return org.springframework.http.HttpMethod.valueOf(method.name());
    }

    private String buildUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = (path == null || path.isBlank()) ? "/" : (path.startsWith("/") ? path : "/" + path);
        return normalizedBase + normalizedPath;
    }

    private int elapsedMillis(Instant started) {
        long elapsed = Duration.between(started, Instant.now()).toMillis();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, elapsed));
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Request failed";
        }
        int max = checkProperties.errorMessageMaxLength();
        return message.length() <= max ? message : message.substring(0, max);
    }

    private boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
