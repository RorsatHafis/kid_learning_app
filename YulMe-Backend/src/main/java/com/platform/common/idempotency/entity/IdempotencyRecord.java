package com.platform.common.idempotency.entity;

import java.time.Instant;
import java.util.UUID;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends AuditableEntity {
 
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;
 
    @Column(name = "operation", nullable = false, updatable = false, length = 100)
    private String operation;
 
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;
 
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IdempotencyStatus status;
 
    @Column(name = "completed_at")
    private Instant completedAt;
 
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
        // JPA
    }
 
    private IdempotencyRecord(UUID actorId, String operation, String idempotencyKey,
                               String requestFingerprint, Instant expiresAt) {

        this.actorId = actorId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.expiresAt = expiresAt;

    }

    public static IdempotencyRecord begin(UUID actorId, String operation, String idempotencyKey,
                                           String requestFingerprint, Instant expiresAt) {

        return new IdempotencyRecord(actorId, operation, idempotencyKey, requestFingerprint, expiresAt);

    }
 
    public void markCompleted(Instant completedAt) {

        requireStatus(IdempotencyStatus.IN_PROGRESS, "completed");
        this.status = IdempotencyStatus.COMPLETED;
        this.completedAt = completedAt;

    }
 
    public void markFailed(Instant completedAt) {

        requireStatus(IdempotencyStatus.IN_PROGRESS, "marked failed");
        this.status = IdempotencyStatus.FAILED;
        this.completedAt = completedAt;

    }

    public void restart(String requestFingerprint, Instant expiresAt) {

        requireStatus(IdempotencyStatus.FAILED, "restarted");
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.requestFingerprint = requestFingerprint;
        this.completedAt = null;
        this.expiresAt = expiresAt;

    }
 
    private void requireStatus(IdempotencyStatus required, String attemptedTransition) {

        if (status != required) {

            throw new IllegalStateException("Record %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));

        }

    }
 
    public UUID getActorId() {

        return actorId;

    }
 
    public String getOperation() {

        return operation;

    }
 
    public String getIdempotencyKey() {

        return idempotencyKey;

    }
 
    public String getRequestFingerprint() {

        return requestFingerprint;

    }
 
    public IdempotencyStatus getStatus() {

        return status;

    }
 
    public Instant getCompletedAt() {

        return completedAt;

    }
 
    public Instant getExpiresAt() {

        return expiresAt;

    }
    
}
