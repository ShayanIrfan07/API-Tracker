package com.apitracker.monitor.validation;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * Validates monitored endpoint URLs before persistence and outbound checks.
 * Blocks SSRF-prone targets such as loopback, link-local, and private networks.
 */
public final class MonitorUrlValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "localhost.localdomain",
            "metadata",
            "metadata.google.internal",
            "0.0.0.0");

    private MonitorUrlValidator() {
    }

    public static void validate(String normalizedBaseUrl, String normalizedPath) {
        validate(normalizedBaseUrl, normalizedPath, true);
    }

    public static void validate(String normalizedBaseUrl, String normalizedPath, boolean enforceSsrfProtection) {
        URI uri = parseAndValidateFormat(normalizedBaseUrl, normalizedPath);
        if (enforceSsrfProtection) {
            validateSsrfSafe(uri);
        }
    }

    private static URI parseAndValidateFormat(String normalizedBaseUrl, String normalizedPath) {
        if (!StringUtils.hasText(normalizedBaseUrl)) {
            throw new IllegalArgumentException("baseUrl is required");
        }

        URI uri;
        try {
            uri = URI.create(normalizedBaseUrl + normalizedPath);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("baseUrl must use http or https");
        }

        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("baseUrl must include a valid host");
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URL must not include embedded credentials");
        }

        return uri;
    }

    private static void validateSsrfSafe(URI uri) {
        String host = normalizeHost(uri.getHost());
        if (isBlockedHostname(host)) {
            throw new IllegalArgumentException("URL host is not allowed for monitoring: " + host);
        }

        if (isBlockedLiteralAddress(host)) {
            throw new IllegalArgumentException("URL resolves to a disallowed network address");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("URL resolves to a disallowed network address");
                }
            }
        } catch (UnknownHostException ex) {
            // Unresolvable public hostnames fail naturally during the HTTP check.
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isBlockedHostname(String host) {
        if (BLOCKED_HOSTS.contains(host)) {
            return true;
        }
        return host.endsWith(".localhost") || host.endsWith(".local");
    }

    private static boolean isBlockedLiteralAddress(String host) {
        try {
            return isBlockedAddress(InetAddress.getByName(host));
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet4Address inet4) {
            byte[] bytes = inet4.getAddress();
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 0) {
                return true;
            }
            if (first == 169 && second == 254) {
                return true;
            }
        }

        if (address instanceof Inet6Address inet6) {
            byte first = inet6.getAddress()[0];
            if ((first & 0xFE) == 0xFC) {
                return true;
            }
        }

        return false;
    }
}
