package com.platform.learner.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.UUID;

/**
 * Battle 5D: the adaptive decision the server made when one activity attempt
 * completed - CONTINUE, REMEDIATE (reinforcement) or INCREASE_DIFFICULTY
 * (challenge) - and the path item it placed, if any. At most one per attempt
 * ({@code uq_adaptation_decisions_activity_attempt}, V36), and immutable
 * ({@code prevent_row_mutation()} trigger, V17), so a retried or concurrent
 * completion can never record a second or different decision.
 *
 * <p>{@code context} holds the decision's inputs: the skill that governed it and the
 * skill it targets.
 */
@Entity
@Table(name = "adaptation_decisions")
public class AdaptationDecision extends ImmutableEvent {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, updatable = false, length = 30)
    private AdaptationDecisionType decisionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", nullable = false, updatable = false)
    private Map<String, Object> context;

    @Column(name = "selected_activity_version_id", updatable = false)
    private UUID selectedActivityVersionId;

    @Column(name = "reason", updatable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "activity_attempt_id", updatable = false)
    private UUID activityAttemptId;

    @Column(name = "learning_path_item_id", updatable = false)
    private UUID learningPathItemId;

    protected AdaptationDecision() {
        // JPA
    }

    private AdaptationDecision(UUID childId, UUID activityAttemptId, AdaptationDecisionType decisionType,
                               Map<String, Object> context, UUID selectedActivityVersionId,
                               UUID learningPathItemId, String reason) {
        this.childId = childId;
        this.activityAttemptId = activityAttemptId;
        this.decisionType = decisionType;
        this.context = context;
        this.selectedActivityVersionId = selectedActivityVersionId;
        this.learningPathItemId = learningPathItemId;
        this.reason = reason;
    }

    public static AdaptationDecision forCompletion(UUID childId, UUID activityAttemptId, AdaptationDecisionType decisionType,
                                                   Map<String, Object> context, UUID selectedActivityVersionId,
                                                   UUID learningPathItemId, String reason) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(activityAttemptId, "activityAttemptId must not be null");
        Assert.notNull(decisionType, "decisionType must not be null");
        Assert.notNull(context, "context must not be null");
        return new AdaptationDecision(childId, activityAttemptId, decisionType, context, selectedActivityVersionId,
                learningPathItemId, reason);
    }

    public UUID getChildId() {
        return childId;
    }

    public AdaptationDecisionType getDecisionType() {
        return decisionType;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public UUID getSelectedActivityVersionId() {
        return selectedActivityVersionId;
    }

    public String getReason() {
        return reason;
    }

    public UUID getActivityAttemptId() {
        return activityAttemptId;
    }

    public UUID getLearningPathItemId() {
        return learningPathItemId;
    }

}
