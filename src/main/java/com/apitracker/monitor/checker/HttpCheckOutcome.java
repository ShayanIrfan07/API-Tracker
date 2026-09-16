package com.apitracker.monitor.checker;

public record HttpCheckOutcome(
        boolean success,
        Integer httpStatus,
        Integer latencyMs,
        String errorMessage,
        boolean timedOut
) {
    public static HttpCheckOutcome ok(int httpStatus, int latencyMs) {
        return new HttpCheckOutcome(true, httpStatus, latencyMs, null, false);
    }

    public static HttpCheckOutcome failed(Integer httpStatus, Integer latencyMs, String errorMessage) {
        return failed(httpStatus, latencyMs, errorMessage, false);
    }

    public static HttpCheckOutcome failed(
            Integer httpStatus, Integer latencyMs, String errorMessage, boolean timedOut) {
        return new HttpCheckOutcome(false, httpStatus, latencyMs, errorMessage, timedOut);
    }
}
