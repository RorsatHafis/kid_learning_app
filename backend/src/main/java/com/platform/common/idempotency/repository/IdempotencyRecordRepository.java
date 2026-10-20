package com.platform.common.idempotency.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.common.idempotency.entity.IdempotencyRecord;

/**
 * Persistence for idempotency records, unique by actor, operation and key.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

     Optional<IdempotencyRecord> findByActorIdAndOperationAndIdempotencyKey(
            UUID actorId, String operation, String idempotencyKey);
    
}
