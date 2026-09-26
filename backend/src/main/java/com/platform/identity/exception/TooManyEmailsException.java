package com.platform.identity.exception;

import java.time.Duration;

/** The signed-in account asked for another email too soon; {@link #retryAfter()} says when it may ask again. */
public class TooManyEmailsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyEmailsException(Duration retryAfter) {
        super("An email was sent recently. Please check your inbox, or try again shortly.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

}
