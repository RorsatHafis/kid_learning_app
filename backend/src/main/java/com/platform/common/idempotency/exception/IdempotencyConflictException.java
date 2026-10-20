package com.platform.common.idempotency.exception;

/**
 * Base for idempotency refusals (answered as 409): the request can't run because of an earlier one with the same
 * key.
 */
public abstract sealed class IdempotencyConflictException extends RuntimeException
        permits DuplicateRequestInProgressException, IdempotencyKeyReusedException {

    private final String operation;
    private final String idempotencyKey;
 
    protected IdempotencyConflictException(String message, String operation, String idempotencyKey) {

        super(message);
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;

    }
 
    public String getOperation() {

        return operation;

    }
 
    public String getIdempotencyKey() {

        return idempotencyKey;

    }
    
}
