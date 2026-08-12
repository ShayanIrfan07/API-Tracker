package com.apitracker.config;

import com.apitracker.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits inbound admin API traffic by client IP.
 * Stricter buckets for login and check-now.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!rateLimitService.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String clientKey = clientKey(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        int limit;
        String bucketName;
        if ("POST".equalsIgnoreCase(method) && "/api/v1/auth/login".equals(path)) {
            limit = rateLimitService.loginPermitsPerMinute();
            bucketName = "login";
        } else if ("POST".equalsIgnoreCase(method) && path != null && path.matches(".*/monitored-apis/[^/]+/check-now")) {
            limit = rateLimitService.checkNowPermitsPerMinute();
            bucketName = "check-now";
        } else {
            limit = rateLimitService.apiPermitsPerMinute();
            bucketName = "api";
        }

        String bucketKey = bucketName + ":" + clientKey;
        if (!rateLimitService.tryConsume(bucketKey, limit)) {
            writeTooManyRequests(response, path, limit);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private void writeTooManyRequests(HttpServletResponse response, String path, int limit) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "TOO_MANY_REQUESTS",
                "Rate limit exceeded (" + limit + " requests per minute). Try again shortly.",
                path);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
