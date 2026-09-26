package com.platform.parent.service;

import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.SkillService;
import com.platform.learner.entity.LearnerObjectiveState;
import com.platform.learner.entity.LearnerObjectiveStatus;
import com.platform.learner.repository.LearnerObjectiveStateRepository;
import com.platform.learner.service.MasteryEngine;
import com.platform.learner.service.MasteryEngine.ObjectiveEvidence;
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
 *
 * <p>Battle 2C: a MASTERED {@link LearnerObjectiveState} is a claim about the
 * *whole* objective (Battle 5C: every mapped skill observed, average at the
 * threshold) - so it can be numerically true while still being backed by only a
 * couple of scored answers on some contributing skill, which is not enough evidence
 * to tell a parent their child has mastered something. {@link #objectiveInsight}
 * checks the objective's minimum per-skill evidence before emitting that specific
 * claim and hedges instead when it isn't met - mirroring the same "evidenceCount
 * indicates real confidence, not a lucky first answer" gate {@link
 * com.platform.learner.service.AdaptiveEngine} already applies before CHALLENGE,
 * reusing its threshold (3) rather than inventing a new number.
 *
 * <p>Battle 5C: the WEAKNESS / "good progress" claims are claims about SOME of the
 * objective, not all of it, so - as 2C intended - they do not require every skill
 * to be well-evidenced. But they must rest on evidence that exists: they are judged
 * only on the skills with sufficient evidence (the same 3 answers), and no such claim
 * is made while no skill has that much. Before 5C they read a rollup that averaged
 * unpractised skills in as 0, so a child with perfect evidence on 1 of 4 skills was
 * told they were "still developing confidence" - "not assessed" read as "failed".
 *
 * <p>Current interpretation vs history: {@link #currentInsights} derives what is true
 * NOW from current learner state and writes nothing; {@link #generateInsights}
 * additionally records that interpretation as an immutable history snapshot. Stored
 * snapshots are historical events - they must never be presented as the child's
 * current state (a corrected learner state would otherwise leave stale claims visible).
 */
@Service
public class ParentInsightService {

    /** At or above this, an in-progress objective reads as "good progress"; below it, "still developing". */
    private static final BigDecimal GOOD_PROGRESS_THRESHOLD = BigDecimal.valueOf(0.6);

    /**
     * Same value and same justification as {@code AdaptiveEngine.MIN_EVIDENCE_FOR_CHALLENGE}:
     * enough scored answers that a high average isn't just a lucky first attempt.
     * Duplicated rather than imported - AdaptiveEngine's constant is private, and
     * this battle is explicitly not a redesign of that class.
     */
    private static final int MIN_EVIDENCE_FOR_MASTERY_CLAIM = 3;
    // W5: one rule for every direction - a claim needs this much evidence on each skill it is
    // judged on, and "mastered" needs it on every skill of the objective.

    /**
     * Battle 5C: the evidence a skill needs before it can support a parent-facing
     * performance claim ("good progress" / "still developing"). Deliberately the same
     * number as the mastery claim: a claim needs the same support whichever direction
     * it points.
     */
    private static final int MIN_EVIDENCE_FOR_PERFORMANCE_CLAIM = MIN_EVIDENCE_FOR_MASTERY_CLAIM;

    private final LearnerObjectiveStateRepository learnerObjectiveStateRepository;
    private final LearningObjectiveService learningObjectiveService;
    private final SmartReviewService smartReviewService;
    private final SkillService skillService;
    private final ParentInsightRepository parentInsightRepository;
    private final MasteryEngine masteryEngine;

    public ParentInsightService(LearnerObjectiveStateRepository learnerObjectiveStateRepository,
                                 LearningObjectiveService learningObjectiveService,
                                 SmartReviewService smartReviewService, SkillService skillService,
                                 ParentInsightRepository parentInsightRepository, MasteryEngine masteryEngine) {
        this.learnerObjectiveStateRepository = learnerObjectiveStateRepository;
        this.learningObjectiveService = learningObjectiveService;
        this.smartReviewService = smartReviewService;
        this.skillService = skillService;
        this.parentInsightRepository = parentInsightRepository;
        this.masteryEngine = masteryEngine;
    }

    /**
     * The child's current interpretation, derived from current learner state and not
     * persisted (so the returned insights have no id / occurredAt). This is what a
     * parent should see as "how my child is doing now".
     */
    @Transactional(readOnly = true)
    public List<ParentInsight> currentInsights(UUID childId) {
        return buildInsights(childId);
    }

    /**
     * Derives the current interpretation (see {@link #currentInsights}) and records it
     * as a fresh batch of immutable history rows (Section 26: a superseded insight is
     * a new row, never an edit), returning exactly what it wrote.
     */
    @Transactional
    public List<ParentInsight> generateInsights(UUID childId) {
        return parentInsightRepository.saveAll(buildInsights(childId));
    }

    /** Every recorded snapshot, most recent first - history, not the current state. */
    @Transactional(readOnly = true)
    public List<ParentInsight> listRecent(UUID childId) {
        return parentInsightRepository.findByChildIdOrderByOccurredAtDesc(childId);
    }

    /**
     * One insight per learning objective with enough evidence to say something
     * truthful (see {@link #objectiveInsight}), plus one RECOMMENDATION per skill
     * currently flagged ACTIVE for Smart Review. Nothing is persisted here.
     */
    private List<ParentInsight> buildInsights(UUID childId) {
        List<ParentInsight> insights = new ArrayList<>();

        for (LearnerObjectiveState state : learnerObjectiveStateRepository.findByChildId(childId)) {
            objectiveInsight(childId, state).ifPresent(insights::add);
        }

        for (ReviewItem reviewItem : smartReviewService.listActive(childId)) {
            insights.add(reviewInsight(childId, reviewItem));
        }

        return insights;
    }

    private Optional<ParentInsight> objectiveInsight(UUID childId, LearnerObjectiveState state) {
        if (state.getStatus() == LearnerObjectiveStatus.NOT_STARTED) {
            return Optional.empty();
        }

        LearningObjective objective = learningObjectiveService.getById(state.getLearningObjectiveId());
        ObjectiveEvidence evidence =
                masteryEngine.objectiveEvidence(childId, objective.getId(), MIN_EVIDENCE_FOR_PERFORMANCE_CLAIM);
        Map<String, Object> supportingData = evidenceMap(objective.getId(), state.getMasteryProbability(), evidence);

        // A mastery claim is about the whole objective, so every skill needs sufficient evidence.
        if (state.getStatus() == LearnerObjectiveStatus.MASTERED
                && evidence.minEvidenceCount() >= MIN_EVIDENCE_FOR_MASTERY_CLAIM) {
            return Optional.of(ParentInsight.generate(childId, ParentInsightType.STRENGTH,
                    "Your child has mastered " + objective.getTitle() + ".", null, supportingData));
        }

        // Otherwise - IN_PROGRESS, or a MASTERED average resting on thin evidence - any claim is
        // judged only on the skills with sufficient evidence (see class javadoc), and there is
        // no claim at all until one skill has that much. A numerically MASTERED objective built
        // on one or two answers per skill used to read "showing early strength" (PD-21): more
        // than the evidence supports, and inconsistent with the same answers on one skill,
        // which rightly claim nothing.
        if (evidence.sufficientlyEvidencedMastery().isEmpty()) {
            return Optional.empty();
        }
        boolean goodProgress = evidence.sufficientlyEvidencedMastery().get().compareTo(GOOD_PROGRESS_THRESHOLD) >= 0;
        ParentInsightType type = goodProgress ? ParentInsightType.PROGRESS : ParentInsightType.WEAKNESS;
        // The claim covers the skills it was judged on; when that is only part of the
        // objective, the sentence says so rather than speaking for the whole objective.
        boolean wholeObjective = evidence.skillsSufficientlyEvidenced() == evidence.skillsMapped();
        String scope = wholeObjective ? objective.getTitle() : "the parts of " + objective.getTitle() + " practised so far";
        String headline = goodProgress
                ? "Your child is making good progress in " + scope + "."
                : "Your child is still developing confidence with " + scope + ".";

        return Optional.of(ParentInsight.generate(childId, type, headline, null, supportingData));
    }

    private ParentInsight reviewInsight(UUID childId, ReviewItem reviewItem) {
        Skill skill = skillService.getSkill(reviewItem.getSkillId());
        Map<String, Object> supportingData = new LinkedHashMap<>();
        supportingData.put("skillId", String.valueOf(skill.getId()));
        supportingData.put("reviewItemId", String.valueOf(reviewItem.getId()));

        return ParentInsight.generate(childId, ParentInsightType.RECOMMENDATION,
                skill.getName() + " may benefit from additional practice.", null, supportingData);
    }

    /**
     * The evidence behind an objective insight, dimension by dimension, so the claim
     * can be explained: observed performance, coverage, and evidence strength.
     */
    private static Map<String, Object> evidenceMap(UUID learningObjectiveId, BigDecimal masteryProbability,
                                                     ObjectiveEvidence evidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("learningObjectiveId", String.valueOf(learningObjectiveId));
        map.put("masteryProbability", masteryProbability.doubleValue());
        map.put("minEvidenceCount", evidence.minEvidenceCount());
        map.put("skillsMapped", evidence.skillsMapped());
        map.put("skillsWithEvidence", evidence.skillsObserved());
        map.put("skillsWithSufficientEvidence", evidence.skillsSufficientlyEvidenced());
        evidence.sufficientlyEvidencedMastery().ifPresent(value -> map.put("sufficientlyEvidencedMastery", value.doubleValue()));
        return map;
    }

}
