package com.platform.common.idempotency.service;

import com.platform.common.idempotency.entity.IdempotencyRecord;

/**
 * What IdempotencyService decided for a request: start it now, or it already completed (return the first result).
 */
public interface IdempotencyOutcome {
    
    record Started(IdempotencyRecord record) implements IdempotencyOutcome {}

    record AlreadyCompleted(IdempotencyRecord record) implements IdempotencyOutcome {}

}
