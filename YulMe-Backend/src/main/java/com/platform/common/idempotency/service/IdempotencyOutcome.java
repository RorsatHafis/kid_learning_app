package com.platform.common.idempotency.service;

import com.platform.common.idempotency.entity.IdempotencyRecord;

public interface IdempotencyOutcome {
    
    record Started(IdempotencyRecord record) implements IdempotencyOutcome {}

    record AlreadyCompleted(IdempotencyRecord record) implements IdempotencyOutcome {}

}
