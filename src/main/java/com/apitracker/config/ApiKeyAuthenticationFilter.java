package com.apitracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API-key filter for admin REST APIs.
 * Runs after JWT filter. Skips when already authenticated or when a Bearer token was presented.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final String configuredApiKey;
    private final boolean requireApiKey;

    public ApiKeyAuthenticationFilter(String configuredApiKey, boolean requireApiKey) {
        this.configuredApiKey = configuredApiKey;
        this.requireApiKey = requireApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/v1/auth/login")) {
            return true;
        }
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
            return true;
        }
        if (path.equals("/") || path.equals("/index.html") || path.equals("/favicon.ico") || path.equals("/error")) {
            return true;
        }
        return path.startsWith("/css/") || path.startsWith("/js/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            // Bearer was present but JWT filter did not authenticate — reject.
            writeUnauthorized(response, "Invalid or expired JWT");
            return;
        }

        if (!StringUtils.hasText(configuredApiKey)) {
            if (requireApiKey) {
                writeUnauthorized(response, "Authentication required (JWT login or API key)");
                return;
            }
            authenticateAsAdmin("dev-open-access");
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(API_KEY_HEADER);
        if (configuredApiKey.equals(provided)) {
            authenticateAsAdmin("api-key-user");
            filterChain.doFilter(request, response);
            return;
        }

        writeUnauthorized(response, "Missing or invalid API key (or login with JWT)");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"status":401,"error":"UNAUTHORIZED","message":"%s"}
                """.formatted(message.replace("\"", "'")));
    }

    private void authenticateAsAdmin(String principal) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
