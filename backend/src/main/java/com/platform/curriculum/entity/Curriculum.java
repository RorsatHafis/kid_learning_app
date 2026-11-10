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

/**
 * A curriculum: a subject for one age band, owned by the platform or by a teacher. Its content is in its versions;
 * archiving keeps them.
 */
@Entity
@Table(name = "curriculums")
public class Curriculum extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    /** Older curricula's age group (V10), kept as a label; a new curriculum needs none (V90). */
    @Column(name = "age_hub_id", updatable = false)
    private UUID ageHubId;

    /** V90 (owner 2026-11-10): the ages the curriculum is made for - what discovery and eligibility use. */
    @Column(name = "min_age", nullable = false)
    private Integer minAge;

    /** Null: no upper age. */
    @Column(name = "max_age")
    private Integer maxAge;

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

    private Curriculum(UUID subjectId, UUID ageHubId, String name, UUID ownerAccountId, int minAge, Integer maxAge) {
        this.subjectId = subjectId;
        this.ageHubId = ageHubId;
        changeAges(minAge, maxAge);
        this.name = name;
        this.ownerAccountId = ownerAccountId;
        this.status = CatalogStatus.ACTIVE;
    }

    /** A curriculum for the ages {@code minAge}-{@code maxAge} (null: no upper age); {@code ageHubId} is an optional label. */
    public static Curriculum create(UUID subjectId, UUID ageHubId, String name, int minAge, Integer maxAge, UUID ownerAccountId) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.hasText(name, "name must not be blank");
        return new Curriculum(subjectId, ageHubId, name, ownerAccountId, minAge, maxAge);
    }

    /** The ages it is made for (min at least 0; max, when given, not below min). */
    public void changeAges(int minAge, Integer maxAge) {
        if (minAge < 0) {
            throw new IllegalArgumentException("The minimum age can't be below 0.");
        }
        if (maxAge != null && maxAge < minAge) {
            throw new IllegalArgumentException("The maximum age can't be below the minimum age.");
        }
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    /** Whether a child of {@code age} is in the curriculum's range. */
    public boolean fitsAge(int age) {
        return age >= minAge && (maxAge == null || age <= maxAge);
    }

    public int getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
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