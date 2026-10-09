package com.platform.content.review;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One request for a second person's review of a draft version (V53, Y14). */
@Entity
@Table(name = "content_reviews")
public class ContentReview extends AuditableEntity {

    public enum Kind { QUESTION, ACTIVITY, CURRICULUM }

    public enum Status { OPEN, APPROVED, CHANGES_REQUESTED, WITHDRAWN }

    @Enumerated(EnumType.STRING)
    @Column(name = "content_kind", nullable = false, updatable = false, length = 20)
    private Kind kind;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fingerprint", nullable = false, updatable = false, length = 64)
    private String fingerprint;

    @Column(name = "requested_by_account_id", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "decided_by_account_id")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "note", length = 1000)
    private String note;

    protected ContentReview() {
        // JPA
    }

    static ContentReview open(Kind kind, UUID versionId, String fingerprint, UUID requestedBy, Instant at) {
        ContentReview review = new ContentReview();
        review.kind = kind;
        review.versionId = versionId;
        review.fingerprint = fingerprint;
        review.requestedBy = requestedBy;
        review.requestedAt = at;
        review.status = Status.OPEN;
        return review;
    }

    void decide(Status outcome, UUID by, String note, Instant at) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("This review has already been answered");
        }
        this.status = outcome;
        this.decidedBy = by;
        this.note = note == null || note.isBlank() ? null : note.strip();
        this.decidedAt = at;
    }

    public Kind getKind() {
        return kind;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getNote() {
        return note;
    }

}
