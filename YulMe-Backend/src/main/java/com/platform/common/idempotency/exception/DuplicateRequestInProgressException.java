package com.platform.common.idempotency.exception;

public final class DuplicateRequestInProgressException extends IdempotencyConflictException {

    public DuplicateRequestInProgressException(String operation, String idempotencyKey) {

        super("A request for operation '%s' with idempotency key '%s' is already being processed"
                .formatted(operation, idempotencyKey), operation, idempotencyKey);

    }
    
}
