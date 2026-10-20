package com.platform.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

/**
 * Base for versioned content (questions, activities, curricula, assessments): a version number and its publication
 * lifecycle. A published version is never edited; a change is a new version.
 */
@MappedSuperclass
public abstract class PublishableVersion extends AuditableEntity {
 
    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PublicationStatus status;
 
    @Column(name = "published_at")
    private Instant publishedAt;
 
    protected PublishableVersion() {
        // JPA
    }
 
    protected PublishableVersion(int versionNumber) {
        if (versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber must be positive, was " + versionNumber);
        }
        this.versionNumber = versionNumber;
        this.status = PublicationStatus.DRAFT;
    }
 
    public void publish(Instant at) {
        requireStatus(PublicationStatus.DRAFT, "published");
        this.status = PublicationStatus.PUBLISHED;
        this.publishedAt = at;
    }
 
    public void retire() {
        requireStatus(PublicationStatus.PUBLISHED, "retired");
        this.status = PublicationStatus.RETIRED;
    }

    /**
     * Battle 3R: whether this version has ever been learner-visible - PUBLISHED, or
     * RETIRED. RETIRED means superseded: {@link #retire()} is only called when the
     * next version of the same content is published. A published version is
     * immutable, and learners hold exact references to it (learning path items,
     * attempts, answers, the question versions an activity version pins), so it
     * remains a valid snapshot for those references. Only DRAFT content has never
     * been visible.
     *
     * <p>Selecting content for new work is a different question: that always uses
     * the current PUBLISHED version.
     */
    public boolean wasPublished() {
        return status == PublicationStatus.PUBLISHED || status == PublicationStatus.RETIRED;
    }
 
    protected final void requireStatus(PublicationStatus required, String attemptedTransition) {
        if (status != required) {
            throw new IllegalStateException("Version %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }
 
    public int getVersionNumber() {
        return versionNumber;
    }
 
    public PublicationStatus getStatus() {
        return status;
    }
 
    public Instant getPublishedAt() {
        return publishedAt;
    }
 
}
 
