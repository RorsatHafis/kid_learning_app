package com.platform.parent.entity;

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
 * A deterministically-generated, plain-language explanation of a child's learning
 * (Section 21/26: "explain data", "every insight must be backed by actual stored
 * evidence"). {@code supportingData} is exactly that backing evidence (e.g. the
 * LearningObjectiveState or ReviewItem the headline was derived from) - see
 * {@link com.platform.parent.service.ParentInsightService} for what gets put there.
 * Immutable once generated, matching the {@code prevent_row_mutation()} trigger on
 * {@code parent_insights} (V19 migration): a superseded insight is a new row, the
 * old one stays as history, exactly like {@link com.platform.learner.entity.LearnerSkillHistory}.
 */
@Entity
@Table(name = "parent_insights")
public class ParentInsight extends ImmutableEvent {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "insight_type", nullable = false, length = 30)
    private ParentInsightType insightType;

    @Column(name = "headline", nullable = false, columnDefinition = "text")
    private String headline;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supporting_data", nullable = false)
    private Map<String, Object> supportingData;

    protected ParentInsight() {
        // JPA
    }

    private ParentInsight(UUID childId, ParentInsightType insightType, String headline, String detail,
                           Map<String, Object> supportingData) {
        this.childId = childId;
        this.insightType = insightType;
        this.headline = headline;
        this.detail = detail;
        this.supportingData = supportingData;
    }

    public static ParentInsight generate(UUID childId, ParentInsightType insightType, String headline, String detail,
                                          Map<String, Object> supportingData) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(insightType, "insightType must not be null");
        Assert.hasText(headline, "headline must not be blank");
        return new ParentInsight(childId, insightType, headline, detail,
                supportingData == null ? Map.of() : supportingData);
    }

    public UUID getChildId() {
        return childId;
    }

    public ParentInsightType getInsightType() {
        return insightType;
    }

    public String getHeadline() {
        return headline;
    }

    public String getDetail() {
        return detail;
    }

    public Map<String, Object> getSupportingData() {
        return supportingData;
    }

}
