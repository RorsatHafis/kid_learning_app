package com.platform.content.entity;

import com.platform.common.entity.PublishableVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "activity_versions")
public class ActivityVersion extends PublishableVersion {

    @Column(name = "activity_id", nullable = false, updatable = false)
    private UUID activityId;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    /** 1-10 inclusive when present; nullable since not every activity type is meaningfully leveled. */
    @Column(name = "difficulty_level")
    private Integer difficultyLevel;

    @Column(name = "estimated_duration_seconds")
    private Integer estimatedDurationSeconds;

    protected ActivityVersion() {
        // JPA
    }

    private ActivityVersion(UUID activityId, int versionNumber, String instructions,
                             Integer difficultyLevel, Integer estimatedDurationSeconds) {
        super(versionNumber);
        this.activityId = activityId;
        this.instructions = instructions;
        this.difficultyLevel = difficultyLevel;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
    }

    public static ActivityVersion draft(UUID activityId, int versionNumber, String instructions,
                                         Integer difficultyLevel, Integer estimatedDurationSeconds) {
        Assert.notNull(activityId, "activityId must not be null");
        Assert.isTrue(difficultyLevel == null || (difficultyLevel >= 1 && difficultyLevel <= 10),
                "difficultyLevel must be between 1 and 10");
        Assert.isTrue(estimatedDurationSeconds == null || estimatedDurationSeconds > 0,
                "estimatedDurationSeconds must be positive");
        return new ActivityVersion(activityId, versionNumber, instructions, difficultyLevel, estimatedDurationSeconds);
    }

    public UUID getActivityId() {
        return activityId;
    }

    public String getInstructions() {
        return instructions;
    }

    public Integer getDifficultyLevel() {
        return difficultyLevel;
    }

    public Integer getEstimatedDurationSeconds() {
        return estimatedDurationSeconds;
    }

}