package com.platform.common.idempotency.service;

import com.platform.common.idempotency.entity.IdempotencyRecord;
import com.platform.common.idempotency.entity.IdempotencyStatus;
import com.platform.common.idempotency.exception.DuplicateRequestInProgressException;
import com.platform.common.idempotency.exception.IdempotencyKeyReusedException;
import com.platform.common.idempotency.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTests {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final String OPERATION = "attempt-submission";
    private static final String KEY = "client-key-1";
    private static final String FINGERPRINT = "a".repeat(64);
    private static final Duration VALID_FOR = Duration.ofHours(24);

    @Mock
    private IdempotencyRecordRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository);
    }

    @Test
    void startsANewGuardWhenNoRecordExists() {
        when(repository.findByActorIdAndOperationAndIdempotencyKey(ACTOR_ID, OPERATION, KEY))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyOutcome outcome = service.begin(ACTOR_ID, OPERATION, KEY, FINGERPRINT, VALID_FOR);

        assertThat(outcome).isInstanceOf(IdempotencyOutcome.Started.class);
        IdempotencyRecord record = ((IdempotencyOutcome.Started) outcome).record();
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(record.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(record.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void reportsAlreadyCompletedForAMatchingRetryOfACompletedOperation() {
        IdempotencyRecord existing = completedRecord(FINGERPRINT);
        when(repository.findByActorIdAndOperationAndIdempotencyKey(ACTOR_ID, OPERATION, KEY))
                .thenReturn(Optional.of(existing));

        IdempotencyOutcome outcome = service.begin(ACTOR_ID, OPERATION, KEY, FINGERPRINT, VALID_FOR);

        assertThat(outcome).isInstanceOf(IdempotencyOutcome.AlreadyCompleted.class);
        assertThat(((IdempotencyOutcome.AlreadyCompleted) outcome).record()).isSameAs(existing);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsAKeyReusedWithADifferentFingerprintAfterCompletion() {
        IdempotencyRecord existing = completedRecord(FINGERPRINT);
        when(repository.findByActorIdAndOperationAndIdempotencyKey(ACTOR_ID, OPERATION, KEY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.begin(ACTOR_ID, OPERATION, KEY, "b".repeat(64), VALID_FOR))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void rejectsARetryWhileTheOriginalIsStillInProgress() {
        IdempotencyRecord existing = IdempotencyRecord.begin(
                ACTOR_ID, OPERATION, KEY, FINGERPRINT, Instant.now().plusSeconds(3600));
        when(repository.findByActorIdAndOperationAndIdempotencyKey(ACTOR_ID, OPERATION, KEY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.begin(ACTOR_ID, OPERATION, KEY, FINGERPRINT, VALID_FOR))
                .isInstanceOf(DuplicateRequestInProgressException.class);
    }

    @Test
    void reclaimsAnOrphanedInProgressGuardPastItsExpiry() {
        IdempotencyRecord existing = IdempotencyRecord.begin(
                ACTOR_ID, OPERATION, KEY, FINGERPRINT, Instant.now().minusSeconds(1));
        when(repository.findByActorIdAndOperationAndIdempotencyKey(ACTOR_ID, OPERATION, KEY))
                .thenReturn(Optional.of(existing));

        IdempotencyOutcome outcome = service.begin(ACTOR_ID, OPERATION, KEY, "new-fingerprint", VALID_FOR);

        assertThat(outcome).isInstanceOf(IdempotencyOutcome.Started.class);
        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(existing.getRequestFingerprint()).isEqualTo("new-fingerprint");
        assertThat(existing.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void restartsAMatchingRetryOfAFailedOperation() {
        IdempotencyRecord existing = failedRecord(FINGERPRINT);
        when(repository.findByActorIdAndOperationAndIdempotencyKey(ACTOR_ID, OPERATION, KEY))
                .thenReturn(Optional.of(existing));

        IdempotencyOutcome outcome = service.begin(ACTOR_ID, OPERATION, KEY, FINGERPRINT, VALID_FOR);

        assertThat(outcome).isInstanceOf(IdempotencyOutcome.Started.class);
        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
    }

    @Test
    void rejectsAKeyReusedWithADifferentFingerprintAfterFailure() {
        IdempotencyRecord existing = failedRecord(FINGERPRINT);
        when(repository.findByActorIdAndOperationAndIdempotencyKey(ACTOR_ID, OPERATION, KEY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.begin(ACTOR_ID, OPERATION, KEY, "b".repeat(64), VALID_FOR))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void completeMarksAnInProgressRecordCompleted() {
        IdempotencyRecord record = IdempotencyRecord.begin(ACTOR_ID, OPERATION, KEY, FINGERPRINT, Instant.now());
        UUID recordId = UUID.randomUUID();
        when(repository.findById(recordId)).thenReturn(Optional.of(record));

        service.complete(recordId);

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    void completeThrowsWhenTheRecordNoLongerExists() {
        UUID recordId = UUID.randomUUID();
        when(repository.findById(recordId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(recordId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failMarksAnExistingRecordFailedByLookingItUpFresh() {
        IdempotencyRecord record = IdempotencyRecord.begin(ACTOR_ID, OPERATION, KEY, FINGERPRINT, Instant.now());
        UUID recordId = UUID.randomUUID();
        when(repository.findById(recordId)).thenReturn(Optional.of(record));

        service.fail(recordId);

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
    }

    @Test
    void failIsANoOpWhenTheRecordNoLongerExists() {
        UUID recordId = UUID.randomUUID();
        when(repository.findById(recordId)).thenReturn(Optional.empty());

        service.fail(recordId);
        // No exception is the assertion: reaching this line without throwing is success.
    }

    private IdempotencyRecord completedRecord(String fingerprint) {
        IdempotencyRecord record = IdempotencyRecord.begin(ACTOR_ID, OPERATION, KEY, fingerprint, Instant.now());
        record.markCompleted(Instant.now());
        return record;
    }

    private IdempotencyRecord failedRecord(String fingerprint) {
        IdempotencyRecord record = IdempotencyRecord.begin(ACTOR_ID, OPERATION, KEY, fingerprint, Instant.now());
        record.markFailed(Instant.now());
        return record;
    }

}