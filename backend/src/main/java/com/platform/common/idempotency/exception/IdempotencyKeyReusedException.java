package com.platform.common.idempotency.exception;

/**
 * An idempotency key was reused for a different request: refused rather than guessing which one was meant.
 */
public final class IdempotencyKeyReusedException extends IdempotencyConflictException {

    public IdempotencyKeyReusedException(String operation, String idempotencyKey) {

        super("Idempotency key '%s' for operation '%s' was already used for a different request"
                .formatted(idempotencyKey, operation), operation, idempotencyKey);

    }
    
}
