package com.platform.common.idempotency.exception;

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
