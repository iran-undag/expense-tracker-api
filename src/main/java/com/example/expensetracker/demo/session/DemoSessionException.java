package com.example.expensetracker.demo.session;

import org.springframework.http.HttpStatus;

public class DemoSessionException extends RuntimeException {

    private static final String CAPACITY_REACHED = "DEMO_CAPACITY_REACHED";
    private static final String SESSION_EXPIRED = "DEMO_SESSION_EXPIRED";
    private static final String SERVICE_UNAVAILABLE = "DEMO_SERVICE_UNAVAILABLE";
    private static final String QUOTA_EXHAUSTED = "DEMO_QUOTA_EXHAUSTED";

    private final String code;
    private final HttpStatus status;
    private final Long retryAfterSeconds;

    private DemoSessionException(
        String code,
        HttpStatus status,
        String message,
        Long retryAfterSeconds,
        Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static DemoSessionException capacityReached(long retryAfterSeconds) {
        return new DemoSessionException(
            CAPACITY_REACHED,
            HttpStatus.TOO_MANY_REQUESTS,
            "Demo capacity is currently unavailable.",
            Math.max(1, retryAfterSeconds),
            null
        );
    }

    public static DemoSessionException sessionExpired() {
        return new DemoSessionException(
            SESSION_EXPIRED,
            HttpStatus.UNAUTHORIZED,
            "The demo session has expired.",
            null,
            null
        );
    }

    public static DemoSessionException serviceUnavailable(Throwable cause) {
        return new DemoSessionException(
            SERVICE_UNAVAILABLE,
            HttpStatus.SERVICE_UNAVAILABLE,
            "The demo service is temporarily unavailable.",
            null,
            cause
        );
    }

    public static DemoSessionException quotaExhausted() {
        return new DemoSessionException(
            QUOTA_EXHAUSTED,
            HttpStatus.TOO_MANY_REQUESTS,
            "The demo action limit has been reached.",
            null,
            null
        );
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
