package com.platform.review.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * "This skill needs spaced reinforcement" for one child. At most one per
 * (child_id, skill_id) - {@code uq_review_items_child_skill} (V18 migration) - so
 * flagging an already-flagged skill again must find and reuse the existing row
 * (retiring/reactivating it), never insert a duplicate. The actual due-date
 * scheduling lives in the paired {@link ReviewSchedule}, not here - this entity is
 * just "is this skill currently under review," not "when."
 */
@Entity
@Table(name = "review_items")
public class ReviewItem extends AuditableEntity {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewItemStatus status;

    protected ReviewItem() {
        // JPA
    }

    private ReviewItem(UUID childId, UUID skillId) {
        this.childId = childId;
        this.skillId = skillId;
        this.status = ReviewItemStatus.ACTIVE;
    }

    public static ReviewItem create(UUID childId, UUID skillId) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        return new ReviewItem(childId, skillId);
    }

    public void retire() {
        this.status = ReviewItemStatus.RETIRED;
    }

    public void reactivate() {
        this.status = ReviewItemStatus.ACTIVE;
    }

    public UUID getChildId() {
        return childId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public ReviewItemStatus getStatus() {
        return status;
    }

}
