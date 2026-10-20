package com.platform.common.idempotency.entity;

/**
 * Where an idempotent operation stands: running, completed, or failed (and may be retried).
 */
public enum IdempotencyStatus {

    IN_PROGRESS,
    COMPLETED,
    FAILED
    
}
