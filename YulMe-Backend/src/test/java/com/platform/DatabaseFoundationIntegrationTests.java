package com.platform;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class DatabaseFoundationIntegrationTests {

    private static final String FINGERPRINT = "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsCreateTheRequiredDatabaseFoundation() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(versions).contains("1", "2", "3");
        assertThat(versions).hasSizeGreaterThanOrEqualTo(3);
        assertThat(tableExists("idempotency_records")).isTrue();
        assertThat(tableExists("audit_events")).isTrue();
        assertThat(constraintExists("idempotency_records_pkey", "p")).isTrue();
        assertThat(constraintExists("audit_events_pkey", "p")).isTrue();
        assertThat(constraintExists("uq_idempotency_records_actor_operation_key", "u")).isTrue();
        assertThat(constraintExists("fk_audit_events_idempotency_record", "f")).isTrue();
        assertThat(indexExists("ix_idempotency_records_expiration")).isTrue();
        assertThat(indexExists("ix_audit_events_actor_occurred_at")).isTrue();
        assertThat(indexExists("ix_audit_events_resource_occurred_at")).isTrue();
        assertThat(indexExists("ix_audit_events_idempotency_record")).isTrue();
        assertThat(columnType("idempotency_records", "created_at")).isEqualTo("timestamp with time zone");
        assertThat(columnType("idempotency_records", "updated_at")).isEqualTo("timestamp with time zone");
        assertThat(columnType("audit_events", "occurred_at")).isEqualTo("timestamp with time zone");
        assertThat(triggerExists("trg_idempotency_records_updated_at")).isTrue();
        assertThat(triggerExists("trg_audit_events_immutable")).isTrue();
    }

    @Test
    void databaseConstraintsAndDeletionRulesProtectSharedState() {
        UUID recordId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        insertIdempotencyRecord(recordId, actorId, "request-1");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM idempotency_records WHERE id = ?", Long.class, recordId)).isZero();
        assertThatThrownBy(() -> insertIdempotencyRecord(UUID.randomUUID(), actorId, "request-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO idempotency_records (
                    id, actor_id, operation, idempotency_key, request_fingerprint, status, expires_at
                ) VALUES (?, ?, 'attempt-submission', 'request-2', 'invalid', 'IN_PROGRESS', now() + interval '1 day')
                """, UUID.randomUUID(), actorId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO idempotency_records (
                    id, actor_id, operation, idempotency_key, request_fingerprint, status, completed_at, expires_at
                ) VALUES (?, ?, 'attempt-submission', 'request-3', ?, 'IN_PROGRESS', now(), now() + interval '1 day')
                """, UUID.randomUUID(), actorId, FINGERPRINT))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID auditEventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO audit_events (
                    id, idempotency_record_id, actor_id, actor_type, action, resource_type, correlation_id
                ) VALUES (?, ?, ?, 'ACCOUNT', 'ATTEMPT_SUBMITTED', 'ATTEMPT', 'test-correlation-id')
                """, auditEventId, recordId, actorId);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM idempotency_records WHERE id = ?", recordId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_events SET action = 'CHANGED' WHERE id = ?", auditEventId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_events WHERE id = ?", auditEventId))
                .isInstanceOf(DataAccessException.class);
    }

    private void insertIdempotencyRecord(UUID recordId, UUID actorId, String key) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_records (
                    id, actor_id, operation, idempotency_key, request_fingerprint, status, expires_at
                ) VALUES (?, ?, 'attempt-submission', ?, ?, 'IN_PROGRESS', now() + interval '1 day')
                """, recordId, actorId, key, FINGERPRINT);
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.' || ?) IS NOT NULL", Boolean.class, tableName));
    }

    private boolean constraintExists(String name, String type) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conname = ? AND contype = ?
                )
                """, Boolean.class, name, type));
    }

    private boolean indexExists(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.' || ?) IS NOT NULL", Boolean.class, name));
    }

    private String columnType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, tableName, columnName);
    }

    private boolean triggerExists(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_trigger
                    WHERE tgname = ? AND NOT tgisinternal
                )
                """, Boolean.class, name));
    }
}
