package com.platform.common.idempotency.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.platform.common.idempotency.entity.IdempotencyRecord;
import com.platform.common.idempotency.exception.DuplicateRequestInProgressException;
import com.platform.common.idempotency.exception.IdempotencyKeyReusedException;
import com.platform.common.idempotency.repository.IdempotencyRecordRepository;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
 
    public IdempotencyService(IdempotencyRecordRepository repository) {

        this.repository = repository;

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyOutcome begin(UUID actorId, String operation, String idempotencyKey,
                                     String requestFingerprint, Duration validFor) {

        Optional<IdempotencyRecord> existing =
                repository.findByActorIdAndOperationAndIdempotencyKey(actorId, operation, idempotencyKey);
 
        if (existing.isEmpty()) {
            
            IdempotencyRecord created = IdempotencyRecord.begin(
                
                    actorId, operation, idempotencyKey, requestFingerprint, Instant.now().plus(validFor));

            return new IdempotencyOutcome.Started(repository.saveAndFlush(created));

        }
 
        IdempotencyRecord record = existing.get();

        return switch (record.getStatus()) {
 
            case COMPLETED -> {

                if (!record.getRequestFingerprint().equals(requestFingerprint)) {

                    throw new IdempotencyKeyReusedException(operation, idempotencyKey);

                }

                yield new IdempotencyOutcome.AlreadyCompleted(record);

            }
 
            case IN_PROGRESS -> {

                if (record.getExpiresAt().isBefore(Instant.now())) {

                    record.markFailed(Instant.now());
                    record.restart(requestFingerprint, Instant.now().plus(validFor));
                    yield new IdempotencyOutcome.Started(record);

                }

                throw new DuplicateRequestInProgressException(operation, idempotencyKey);

            }
 
            case FAILED -> {

                if (!record.getRequestFingerprint().equals(requestFingerprint)) {

                    throw new IdempotencyKeyReusedException(operation, idempotencyKey);

                }
                // Same request retried after a deterministic failure: legitimate to try again.
                // @Version on the entity protects against two concurrent retries both restarting it.
                record.restart(requestFingerprint, Instant.now().plus(validFor));

                yield new IdempotencyOutcome.Started(record);

            }
 
        };

    }

    private IdempotencyRecord insertNew(UUID actorId, String operation, String idempotencyKey,
                                         String requestFingerprint, Duration validFor) {

        IdempotencyRecord created = IdempotencyRecord.begin(
                actorId, operation, idempotencyKey, requestFingerprint, Instant.now().plus(validFor));

        return repository.saveAndFlush(created);

    }

    @Transactional
    public void complete(UUID recordId) {

        IdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("Idempotency record " + recordId + " not found"));

        record.markCompleted(Instant.now());

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID recordId) {

        repository.findById(recordId).ifPresent(record -> record.markFailed(Instant.now()));

    }
    
}
