package com.platform.common.idempotency.exception;

public final class IdempotencyKeyReusedException extends IdempotencyConflictException {

    public IdempotencyKeyReusedException(String operation, String idempotencyKey) {

        super("Idempotency key '%s' for operation '%s' was already used for a different request"
                .formatted(idempotencyKey, operation), operation, idempotencyKey);

    }
    
}
