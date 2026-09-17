package com.platform.parent.service;

import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.SkillService;
import com.platform.learner.entity.LearnerObjectiveState;
import com.platform.learner.entity.LearnerObjectiveStatus;
import com.platform.learner.repository.LearnerObjectiveStateRepository;
import com.platform.parent.entity.ParentInsight;
import com.platform.parent.entity.ParentInsightType;
import com.platform.parent.repository.ParentInsightRepository;
import com.platform.review.entity.ReviewItem;
import com.platform.review.service.SmartReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Section 21: "Parent Insight should explain data" - not "expose raw mastery
 * percentages." Every insight here is a template filled from one specific piece of
 * already-computed evidence ({@link LearnerObjectiveState} or {@link ReviewItem}),
 * with that evidence recorded verbatim in {@code supportingData} - deterministic,
 * explainable, no AI/LLM call anywhere in this class, matching Section 45's
 * platform-wide AI policy as much as any other engine in this codebase.
 */
@Service
public class ParentInsightService {

    /** At or above this, an in-progress objective reads as "good progress"; below it, "still developing". */
    private static final BigDecimal GOOD_PROGRESS_THRESHOLD = BigDecimal.valueOf(0.6);

    private final LearnerObjectiveStateRepository learnerObjectiveStateRepository;
    private final LearningObjectiveService learningObjectiveService;
    private final SmartReviewService smartReviewService;
    private final SkillService skillService;
    private final ParentInsightRepository parentInsightRepository;

    public ParentInsightService(LearnerObjectiveStateRepository learnerObjectiveStateRepository,
                                 LearningObjectiveService learningObjectiveService,
                                 SmartReviewService smartReviewService, SkillService skillService,
                                 ParentInsightRepository parentInsightRepository) {
        this.learnerObjectiveStateRepository = learnerObjectiveStateRepository;
        this.learningObjectiveService = learningObjectiveService;
        this.smartReviewService = smartReviewService;
        this.skillService = skillService;
        this.parentInsightRepository = parentInsightRepository;
    }

    /**
     * Generates one insight per learning objective the child has engaged with
     * (MASTERED -&gt; STRENGTH, IN_PROGRESS -&gt; PROGRESS or WEAKNESS depending on
     * {@link #GOOD_PROGRESS_THRESHOLD}; NOT_STARTED objectives produce nothing -
     * there's no evidence yet to explain), plus one RECOMMENDATION per skill
     * currently flagged ACTIVE for Smart Review. Each call persists a fresh batch of
     * immutable rows (Section 26: a superseded insight is a new row, never an edit)
     * and returns exactly what it wrote - callers that only want the latest picture
     * should call this, not accumulate across calls themselves.
     */
    @Transactional
    public List<ParentInsight> generateInsights(UUID childId) {
        List<ParentInsight> generated = new ArrayList<>();

        for (LearnerObjectiveState state : learnerObjectiveStateRepository.findByChildId(childId)) {
            objectiveInsight(childId, state).ifPresent(generated::add);
        }

        for (ReviewItem reviewItem : smartReviewService.listActive(childId)) {
            generated.add(reviewInsight(childId, reviewItem));
        }

        return generated;
    }

    @Transactional(readOnly = true)
    public List<ParentInsight> listRecent(UUID childId) {
        return parentInsightRepository.findByChildIdOrderByOccurredAtDesc(childId);
    }

    private Optional<ParentInsight> objectiveInsight(UUID childId, LearnerObjectiveState state) {
        if (state.getStatus() == LearnerObjectiveStatus.NOT_STARTED) {
            return Optional.empty();
        }

        LearningObjective objective = learningObjectiveService.getById(state.getLearningObjectiveId());
        Map<String, Object> supportingData = evidenceMap(objective.getId(), state.getMasteryProbability());

        if (state.getStatus() == LearnerObjectiveStatus.MASTERED) {
            return Optional.of(parentInsightRepository.save(ParentInsight.generate(childId,
                    ParentInsightType.STRENGTH, "Your child has mastered " + objective.getTitle() + ".", null,
                    supportingData)));
        }

        boolean goodProgress = state.getMasteryProbability().compareTo(GOOD_PROGRESS_THRESHOLD) >= 0;
        ParentInsightType type = goodProgress ? ParentInsightType.PROGRESS : ParentInsightType.WEAKNESS;
        String headline = goodProgress
                ? "Your child is making good progress in " + objective.getTitle() + "."
                : "Your child is still developing confidence with " + objective.getTitle() + ".";

        return Optional.of(
                parentInsightRepository.save(ParentInsight.generate(childId, type, headline, null, supportingData)));
    }

    private ParentInsight reviewInsight(UUID childId, ReviewItem reviewItem) {
        Skill skill = skillService.getSkill(reviewItem.getSkillId());
        Map<String, Object> supportingData = new LinkedHashMap<>();
        supportingData.put("skillId", String.valueOf(skill.getId()));
        supportingData.put("reviewItemId", String.valueOf(reviewItem.getId()));

        return parentInsightRepository.save(ParentInsight.generate(childId, ParentInsightType.RECOMMENDATION,
                skill.getName() + " may benefit from additional practice.", null, supportingData));
    }

    private static Map<String, Object> evidenceMap(UUID learningObjectiveId, BigDecimal masteryProbability) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("learningObjectiveId", String.valueOf(learningObjectiveId));
        map.put("masteryProbability", masteryProbability.doubleValue());
        return map;
    }

}
