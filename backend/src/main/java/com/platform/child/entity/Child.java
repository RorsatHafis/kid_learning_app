package com.platform.child.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

import org.springframework.util.Assert;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

/**
 * A child in a family: display name, date of birth, language and Learning Buddy, and lifecycle (active, archived,
 * waiting for erasure). Only the family's parents manage it; school access is a separate, explicit grant.
 */
@Entity
@Table(name = "children")
public class Child extends AuditableEntity {

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;
 
    @NotBlank
    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @NotNull
    @PastOrPresent
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChildStatus status;

    /** When a DELETION_PENDING child is erased (V51); null otherwise. */
    @Column(name = "erase_after")
    private Instant eraseAfter;

    /** The language the child's own space is shown in (en or km; V57) - the child's, not the parent's. */
    @Column(name = "preferred_language", nullable = false, length = 5)
    private String preferredLanguage = com.platform.identity.UiLanguage.ENGLISH;

    /** The Learning Buddy the child chose (V58); Momo the otter until they choose. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "learning_buddy", nullable = false, length = 20)
    private LearningBuddy learningBuddy = LearningBuddy.OTTER;
 
    protected Child() {
        // JPA
    }
 
    private Child(UUID familyId, String displayName, LocalDate dateOfBirth) {

        this.familyId = familyId;
        this.displayName = displayName;
        this.dateOfBirth = dateOfBirth;
        this.status = ChildStatus.ACTIVE;

    }
 
    public static Child enroll(UUID familyId, String displayName, LocalDate dateOfBirth) {

        Assert.hasText(displayName, "displayName must not be blank");
        Assert.notNull(dateOfBirth, "dateOfBirth must not be null");
        Assert.isTrue(!dateOfBirth.isAfter(LocalDate.now()), "dateOfBirth must not be in the future");

        return new Child(familyId, displayName, dateOfBirth);

    }
 
    public void rename(String newDisplayName) {

        Assert.hasText(newDisplayName, "newDisplayName must not be blank");
        this.displayName = newDisplayName;

    }

    /** The interface language of the child's space. Never changes which content the child follows. */
    public void changeLanguage(String language) {
        Assert.isTrue(com.platform.identity.UiLanguage.isSupported(language), "unsupported language: " + language);
        this.preferredLanguage = language;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    /** The buddy shown in the child's space. Never changes learning, scoring or what a parent is told. */
    public void chooseBuddy(LearningBuddy buddy) {
        Assert.notNull(buddy, "buddy must not be null");
        this.learningBuddy = buddy;
    }

    public LearningBuddy getLearningBuddy() {
        return learningBuddy;
    }

    /** The family corrects the date of birth (Y12). Same rules as when the child was added. */
    public void correctDateOfBirth(LocalDate corrected) {

        Assert.notNull(corrected, "dateOfBirth must not be null");
        Assert.isTrue(!corrected.isAfter(LocalDate.now()), "The date of birth can't be in the future");
        Assert.isTrue(corrected.isAfter(LocalDate.of(1900, 1, 1)), "Enter the child's real date of birth");
        this.dateOfBirth = corrected;

    }

    public void archive() {

        requireStatus(ChildStatus.ACTIVE, "archived");
        this.status = ChildStatus.ARCHIVED;
        
    }
 
    public void restore() {

        requireStatus(ChildStatus.ARCHIVED, "restored");
        this.status = ChildStatus.ACTIVE;

    }
 
    /** The family asked to delete this child: out of learning now, erased at {@code at}. */
    public void scheduleErasure(Instant at) {

        Assert.notNull(at, "at must not be null");
        requireStatus(ChildStatus.ACTIVE, "scheduled for deletion");
        this.status = ChildStatus.DELETION_PENDING;
        this.eraseAfter = at;

    }

    /** The family kept the child after all. */
    public void cancelErasure() {

        requireStatus(ChildStatus.DELETION_PENDING, "kept");
        this.status = ChildStatus.ACTIVE;
        this.eraseAfter = null;

    }

    public Instant getEraseAfter() {
        return eraseAfter;
    }

    private void requireStatus(ChildStatus required, String attemptedTransition) {

        if (status != required) {

            throw new IllegalStateException("Child %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));

        }

    }

    public int ageInYears(LocalDate asOf) {
        return Period.between(dateOfBirth, asOf).getYears();
    }
 
    public UUID getFamilyId() {
        return familyId;
    }
 
    public String getDisplayName() {
        return displayName;
    }
 
    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }
 
    public ChildStatus getStatus() {
        return status;
    }
    
}
