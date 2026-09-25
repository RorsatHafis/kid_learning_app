package com.platform.audit.repository;

import com.platform.TestcontainersConfiguration;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.entity.AuditEvent;
import com.platform.common.idempotency.entity.IdempotencyRecord;
import com.platform.common.idempotency.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
 
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
 
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class AuditPersistenceIntegrationTests {
 
    @Autowired
    private AuditEventRepository auditEventRepository;
 
    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;
 
    @Test
    void savesAndReloadsNestedJsonbMetadata() {
        Map<String, Object> metadata = Map.of(
                "score", 87,
                "tags", List.of("review", "khmer"),
                "nested", Map.of("attemptNumber", 3));
 
        AuditEvent saved = auditEventRepository.saveAndFlush(AuditEvent.occur(
                null, UUID.randomUUID(), AuditActorType.CHILD, "ATTEMPT_SUBMITTED", "ATTEMPT",
                UUID.randomUUID(), "correlation-" + UUID.randomUUID(), metadata));
 
        AuditEvent reloaded = auditEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMetadata()).containsEntry("score", 87);
        assertThat(reloaded.getMetadata()).containsEntry("tags", List.of("review", "khmer"));
        assertThat(reloaded.getMetadata()).containsEntry("nested", Map.of("attemptNumber", 3));
        assertThat(reloaded.getOccurredAt()).isNotNull();
    }
 
    @Test
    void allowsAnEventWithNoIdempotencyRecordReference() {
        AuditEvent saved = auditEventRepository.saveAndFlush(AuditEvent.occur(
                null, null, AuditActorType.SYSTEM, "REVIEW_SCHEDULED", "REVIEW_SCHEDULE",
                null, "correlation-" + UUID.randomUUID(), Map.of()));
 
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIdempotencyRecordId()).isNull();
    }
 
    @Test
    void acceptsAReferenceToARealIdempotencyRecord() {
        IdempotencyRecord idempotencyRecord = idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.begin(
                UUID.randomUUID(), "attempt-submission", "key-" + UUID.randomUUID(), "a".repeat(64),
                Instant.now().plusSeconds(3600)));
 
        AuditEvent saved = auditEventRepository.saveAndFlush(AuditEvent.occur(
                idempotencyRecord.getId(), null, AuditActorType.PARENT, "ATTEMPT_SUBMITTED", "ATTEMPT",
                null, "correlation-" + UUID.randomUUID(), Map.of()));
 
        assertThat(saved.getIdempotencyRecordId()).isEqualTo(idempotencyRecord.getId());
    }
 
    @Test
    void rejectsAReferenceToANonExistentIdempotencyRecord() {
        AuditEvent invalid = AuditEvent.occur(
                UUID.randomUUID(), null, AuditActorType.PARENT, "ATTEMPT_SUBMITTED", "ATTEMPT",
                null, "correlation-" + UUID.randomUUID(), Map.of());
 
        assertThatThrownBy(() -> auditEventRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
 
}
 
