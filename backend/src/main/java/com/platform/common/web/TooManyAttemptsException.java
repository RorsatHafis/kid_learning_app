package com.platform.common.web;

import java.time.Duration;

/** 429: a per-account budget for an action ran out (e.g. looking up class codes); try again after {@code retryAfter}. */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
