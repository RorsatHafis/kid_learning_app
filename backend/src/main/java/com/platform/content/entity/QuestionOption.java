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

/**
 * One option of a question version: a choice (and whether it is correct), one side of a matching pair, or an item
 * and group for sorting, with optional picture and sound. Never sent to learners with its correctness.
 */
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

    /** SORTING only (V49): a group, or an item belonging to the group with the same number; null otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sort_role", length = 5)
    private SortRole sortRole;

    @Column(name = "sort_group")
    private Integer sortGroup;

    /** X11: a picture standing for the item (its label then describes it, for screen readers), and a recording of it. */
    @Column(name = "image_asset_id")
    private UUID imageAssetId;

    @Column(name = "audio_asset_id")
    private UUID audioAssetId;

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

    /**
     * A group or an item of a SORTING question. Like a matching item, never "correct" on its own:
     * which group an item belongs to is the answer, and it is never sent to a learner.
     */
    public static QuestionOption sortOption(UUID questionVersionId, String label, SortRole role, int group, int displayOrder) {
        Assert.notNull(role, "role must not be null");
        Assert.isTrue(group >= 0, "group must not be negative");
        QuestionOption option = create(questionVersionId, label, false, displayOrder);
        option.sortRole = role;
        option.sortGroup = group;
        return option;
    }

    /** A copy for another version of the same question, pairing, grouping and media included. */
    public QuestionOption copyFor(UUID otherVersionId) {
        QuestionOption copy = create(otherVersionId, label, correct, displayOrder);
        copy.matchSide = matchSide;
        copy.matchPair = matchPair;
        copy.sortRole = sortRole;
        copy.sortGroup = sortGroup;
        copy.imageAssetId = imageAssetId;
        copy.audioAssetId = audioAssetId;
        return copy;
    }

    /** Set on a new option only (options of a draft are replaced as a whole, never edited after publishing). */
    public QuestionOption withMedia(UUID imageAssetId, UUID audioAssetId) {
        this.imageAssetId = imageAssetId;
        this.audioAssetId = audioAssetId;
        return this;
    }

    public UUID getImageAssetId() {
        return imageAssetId;
    }

    public UUID getAudioAssetId() {
        return audioAssetId;
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

    public SortRole getSortRole() {
        return sortRole;
    }

    public Integer getSortGroup() {
        return sortGroup;
    }

}