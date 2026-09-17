package com.platform.curriculum.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "curriculums")
public class Curriculum extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "age_hub_id", nullable = false, updatable = false)
    private UUID ageHubId;

    @Column(name = "owner_account_id", updatable = false)
    private UUID ownerAccountId;

    @NotBlank
    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    protected Curriculum() {
        // JPA
    }

    private Curriculum(UUID subjectId, UUID ageHubId, String name, UUID ownerAccountId) {
        this.subjectId = subjectId;
        this.ageHubId = ageHubId;
        this.name = name;
        this.ownerAccountId = ownerAccountId;
        this.status = CatalogStatus.ACTIVE;
    }

    public static Curriculum create(UUID subjectId, UUID ageHubId, String name) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.notNull(ageHubId, "ageHubId must not be null");
        Assert.hasText(name, "name must not be blank");
        return new Curriculum(subjectId, ageHubId, name, null);
    }

    public void rename(String newName) {
        Assert.hasText(newName, "newName must not be blank");
        this.name = newName;
    }

    public void archive() {
        requireStatus(CatalogStatus.ACTIVE, "archived");
        this.status = CatalogStatus.ARCHIVED;
    }

    public void restore() {
        requireStatus(CatalogStatus.ARCHIVED, "restored");
        this.status = CatalogStatus.ACTIVE;
    }

    private void requireStatus(CatalogStatus required, String attemptedTransition) {
        if (status != required) {
            throw new IllegalStateException("Curriculum %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public static Curriculum createOwned(UUID subjectId, UUID ageHubId, String name, UUID ownerAccountId) {
        Assert.notNull(ownerAccountId, "ownerAccountId must not be null");
        return new Curriculum(subjectId, ageHubId, name, ownerAccountId);
    }

    public UUID getOwnerAccountId() { return ownerAccountId; }

    public UUID getAgeHubId() {
        return ageHubId;
    }

    public String getName() {
        return name;
    }

    public CatalogStatus getStatus() {
        return status;
    }

}