package com.apitracker.monitor.checker;

public record HttpCheckOutcome(
        boolean success,
        Integer httpStatus,
        Integer latencyMs,
        String errorMessage
) {
    public static HttpCheckOutcome ok(int httpStatus, int latencyMs) {
        return new HttpCheckOutcome(true, httpStatus, latencyMs, null);
    }

    public static HttpCheckOutcome failed(Integer httpStatus, Integer latencyMs, String errorMessage) {
        return new HttpCheckOutcome(false, httpStatus, latencyMs, errorMessage);
    }
}
