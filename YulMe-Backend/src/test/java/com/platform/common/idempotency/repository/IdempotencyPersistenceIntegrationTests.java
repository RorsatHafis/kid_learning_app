package com.platform.common.idempotency.repository;

import com.platform.TestcontainersConfiguration;
import com.platform.common.idempotency.entity.IdempotencyRecord;
import com.platform.common.idempotency.entity.IdempotencyStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
 
import java.time.Instant;
import java.util.UUID;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
 
/**
 * Runs against real PostgreSQL (Testcontainers) rather than mocks — the point is to
 * catch entity/schema mismatches and constraint behavior a unit test can't see, since
 * ddd-auto=validate only checks mappings exist, not that runtime behavior matches.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class IdempotencyPersistenceIntegrationTests {
 
    @Autowired
    private IdempotencyRecordRepository repository;
 
    @Test
    void savesAndReloadsARecordWithAllFieldsIntact() {
        UUID actorId = UUID.randomUUID();
        IdempotencyRecord record = IdempotencyRecord.begin(
                actorId, "attempt-submission", "key-" + UUID.randomUUID(), "a".repeat(64),
                Instant.now().plusSeconds(3600));
 
        IdempotencyRecord saved = repository.saveAndFlush(record);
 
        IdempotencyRecord reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActorId()).isEqualTo(actorId);
        assertThat(reloaded.getOperation()).isEqualTo("attempt-submission");
        assertThat(reloaded.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }
 
    @Test
    void uniqueConstraintRejectsADuplicateActorOperationKeyTuple() {
        UUID actorId = UUID.randomUUID();
        String key = "key-" + UUID.randomUUID();
        repository.saveAndFlush(IdempotencyRecord.begin(
                actorId, "attempt-submission", key, "a".repeat(64), Instant.now().plusSeconds(3600)));
 
        IdempotencyRecord duplicate = IdempotencyRecord.begin(
                actorId, "attempt-submission", key, "b".repeat(64), Instant.now().plusSeconds(3600));
 
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
 
    @Test
    void optimisticLockingRejectsAConcurrentUpdateBasedOnAStaleCopy() {
        UUID actorId = UUID.randomUUID();
        IdempotencyRecord saved = repository.saveAndFlush(IdempotencyRecord.begin(
                actorId, "attempt-submission", "key-" + UUID.randomUUID(), "a".repeat(64),
                Instant.now().plusSeconds(3600)));
 
        // Each findById below runs in its own transaction/persistence context (no
        // enclosing @Transactional on this test), so these are two independent
        // in-memory copies — simulating two concurrent requests each loading their own view.
        IdempotencyRecord firstCopy = repository.findById(saved.getId()).orElseThrow();
        IdempotencyRecord secondCopy = repository.findById(saved.getId()).orElseThrow();
 
        firstCopy.markCompleted(Instant.now());
        repository.saveAndFlush(firstCopy);
 
        secondCopy.markFailed(Instant.now());
        assertThatThrownBy(() -> repository.saveAndFlush(secondCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
 
}
 
