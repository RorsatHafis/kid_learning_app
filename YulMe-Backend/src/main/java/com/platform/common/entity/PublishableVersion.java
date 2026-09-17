package com.platform.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

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
 
