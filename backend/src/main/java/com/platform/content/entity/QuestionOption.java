package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "question_options")
public class QuestionOption extends BaseEntity {

    @Column(name = "question_version_id", nullable = false, updatable = false)
    private UUID questionVersionId;

    @NotBlank
    @Column(name = "label", nullable = false, columnDefinition = "text")
    private String label;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** MATCHING only: the column this item is in, and the pair it belongs to (V40); null otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_side", length = 5)
    private MatchSide matchSide;

    @Column(name = "match_pair")
    private Integer matchPair;

    protected QuestionOption() {
        // JPA
    }

    private QuestionOption(UUID questionVersionId, String label, boolean correct, int displayOrder) {
        this.questionVersionId = questionVersionId;
        this.label = label;
        this.correct = correct;
        this.displayOrder = displayOrder;
    }

    public static QuestionOption create(UUID questionVersionId, String label, boolean correct, int displayOrder) {
        Assert.notNull(questionVersionId, "questionVersionId must not be null");
        Assert.hasText(label, "label must not be blank");
        Assert.isTrue(displayOrder >= 0, "displayOrder must not be negative");
        return new QuestionOption(questionVersionId, label, correct, displayOrder);
    }

    /**
     * One side of a MATCHING pair. A matching item is never "correct" on its own: the
     * pairing is the answer, and it is never sent to a learner.
     */
    public static QuestionOption matchItem(UUID questionVersionId, String label, MatchSide side, int pair, int displayOrder) {
        Assert.notNull(side, "side must not be null");
        Assert.isTrue(pair >= 0, "pair must not be negative");
        QuestionOption option = create(questionVersionId, label, false, displayOrder);
        option.matchSide = side;
        option.matchPair = pair;
        return option;
    }

    /** A copy for another version of the same question, pairing included. */
    public QuestionOption copyFor(UUID otherVersionId) {
        QuestionOption copy = create(otherVersionId, label, correct, displayOrder);
        copy.matchSide = matchSide;
        copy.matchPair = matchPair;
        return copy;
    }

    public void relabel(String newLabel) {
        Assert.hasText(newLabel, "newLabel must not be blank");
        this.label = newLabel;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public MatchSide getMatchSide() {
        return matchSide;
    }

    public Integer getMatchPair() {
        return matchPair;
    }

}