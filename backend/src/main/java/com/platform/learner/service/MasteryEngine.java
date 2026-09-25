package com.platform.learner.service;

import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.learner.MasteryProperties;
import com.platform.learner.entity.LearnerObjectiveState;
import com.platform.learner.entity.LearnerSkillHistory;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.entity.EvidenceSource;
import com.platform.learner.repository.LearnerObjectiveStateRepository;
import com.platform.learner.repository.LearnerSkillHistoryRepository;
import com.platform.learner.repository.LearnerSkillStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Phase 1 mastery engine: deterministic, explainable, no ML (Section 16 of the
 * master prompt is explicit that this is preferable to "fake AI" at this stage).
 *
 * Algorithm - bias-corrected EWMA (exponentially weighted moving average) of observed
 * outcomes (Battle 5B):
 * <pre>
 *   outcome = 1.0 if correct, 0.0 if incorrect
 *   n       = evidence count including this observation
 *   step    = learningRate / (1 - (1 - learningRate)^n)
 *   newMastery = oldMastery + step * (outcome - oldMastery)
 * </pre>
 * The result is exactly the recency-weighted mean of the answers actually observed
 * for this skill (weights (1 - learningRate)^age, normalized), so mastery means one
 * thing only: "recent accuracy, from evidence". {@code step} is 1 for the first
 * answer and falls toward {@code learningRate}, so after a few answers this is the
 * plain EWMA - recent evidence still outweighs old evidence (the recency behavior
 * Section 16 asks for).
 *
 * <p>Before Battle 5B the update used {@code learningRate} from the first answer
 * against a starting mastery of 0, so after n answers (1 - learningRate)^n of the
 * number was that starting 0 - an assumption, not evidence (0.8 after one answer,
 * 0.64 after two, 0.51 after three). A child with 2/2 correct read as 0.36 and was
 * sent to remediation and review. How MUCH evidence stands behind the number is a
 * separate question, answered by {@code evidenceCount}/{@code confidence}, and every
 * consumer that acts on mastery gates its decision on evidence count (AdaptiveEngine,
 * SmartReviewService, ParentInsightService). Existing rows were converted by V34.
 *
 * <p>Confidence scales linearly with evidence_count up to a configured saturation
 * point, matching "small amount of evidence -> confidence remains limited."
 *
 * Objective-level rollup is a simple average of the mastery of its skills that have
 * evidence (Battle 5C - an unpractised skill is unknown, not 0), with coverage and
 * evidence strength kept as separate dimensions (see {@link ObjectiveEvidence}). A
 * weighted average (e.g. by skill importance) is a reasonable future refinement, not
 * built speculatively here.
 */
@Service
public class MasteryEngine {

    private final LearnerSkillStateRepository skillStateRepository;
    private final LearnerSkillHistoryRepository skillHistoryRepository;
    private final LearnerObjectiveStateRepository objectiveStateRepository;
    private final LearningObjectiveSkillRepository learningObjectiveSkillRepository;
    private final MasteryProperties properties;

    public MasteryEngine(LearnerSkillStateRepository skillStateRepository,
                          LearnerSkillHistoryRepository skillHistoryRepository,
                          LearnerObjectiveStateRepository objectiveStateRepository,
                          LearningObjectiveSkillRepository learningObjectiveSkillRepository,
                          MasteryProperties properties) {
        this.skillStateRepository = skillStateRepository;
        this.skillHistoryRepository = skillHistoryRepository;
        this.objectiveStateRepository = objectiveStateRepository;
        this.learningObjectiveSkillRepository = learningObjectiveSkillRepository;
        this.properties = properties;
    }

    /**
     * Records one piece of evidence for a skill (a scored answer), updates the
     * current estimate, appends an immutable history snapshot, and rolls the change
     * up to every learning objective this skill feeds into.
     */
    @Transactional
    public LearnerSkillState recordEvidence(UUID childId, UUID skillId, boolean correct, UUID triggeredByAttemptId) {
        LearnerSkillState state = skillStateRepository.findByChildIdAndSkillId(childId, skillId)
                .orElseGet(() -> skillStateRepository.save(LearnerSkillState.initial(childId, skillId)));

        int newEvidenceCount = state.getEvidenceCount() + 1;
        BigDecimal outcome = correct ? BigDecimal.ONE : BigDecimal.ZERO;
        BigDecimal delta = outcome.subtract(state.getMasteryProbability()).multiply(stepSize(newEvidenceCount));
        BigDecimal newMastery = clamp(state.getMasteryProbability().add(delta));

        BigDecimal newConfidence = clamp(
                BigDecimal.valueOf(newEvidenceCount)
                        .divide(BigDecimal.valueOf(properties.confidenceSaturationCount()), 4, RoundingMode.HALF_UP));

        Instant now = Instant.now();
        state.applyEvidence(newMastery, newConfidence, correct, now);

        skillHistoryRepository.save(LearnerSkillHistory.snapshot(
                childId, skillId, newMastery, newConfidence, triggeredByAttemptId, EvidenceSource.ACTIVITY_ATTEMPT));

        recalculateObjectivesForSkill(childId, skillId);

        return state;
    }

    @Transactional(readOnly = true)
    public List<LearnerSkillState> listSkillStates(UUID childId) {
        return skillStateRepository.findByChildId(childId);
    }

    @Transactional(readOnly = true)
    public List<LearnerSkillHistory> getHistory(UUID childId, UUID skillId) {
        return skillHistoryRepository.findByChildIdAndSkillIdOrderByOccurredAtDesc(childId, skillId);
    }

    @Transactional(readOnly = true)
    public List<LearnerObjectiveState> listObjectiveStates(UUID childId) {
        return objectiveStateRepository.findByChildId(childId);
    }

    /**
     * The weakest evidence behind any single skill this objective's rolled-up
     * mastery depends on - i.e. how many scored answers back up the
     * *least*-evidenced skill contributing to {@link #recalculateObjectiveState}'s
     * average. A mapped skill with no {@link LearnerSkillState} row at all (never
     * attempted) counts as zero evidence (Battle 5C: it is excluded from the mastery
     * average itself, but still means the objective's evidence is incomplete). Battle 2C: lets a caller (Parent
     * Insight) tell "this objective's mastery number is backed by real repeated
     * evidence across everything it depends on" from "the average happens to be
     * high, but at least one contributing skill has barely been touched" - a
     * distinction the mastery number alone can't make. Returns 0 for an objective
     * with no skill mappings at all, same as an objective with a real but wholly
     * unattempted mapped skill.
     */
    @Transactional(readOnly = true)
    public int minEvidenceCountForObjective(UUID childId, UUID learningObjectiveId) {
        return objectiveEvidence(childId, learningObjectiveId, 1).minEvidenceCount();
    }

    private void recalculateObjectivesForSkill(UUID childId, UUID skillId) {
        List<LearningObjectiveSkill> mappings = learningObjectiveSkillRepository.findBySkillId(skillId);
        for (LearningObjectiveSkill mapping : mappings) {
            recalculateObjectiveState(childId, mapping.getLearningObjectiveId());
        }
    }

    /**
     * Battle 5C: the three separate dimensions of an objective's learning state for
     * one child - observed performance, coverage, and evidence strength - so callers
     * never have to read them out of a single scalar that conflates them.
     *
     * @param sufficientEvidence answers a skill needs before it counts towards
     *                           {@link ObjectiveEvidence#sufficientlyEvidencedMastery}
     */
    @Transactional(readOnly = true)
    public ObjectiveEvidence objectiveEvidence(UUID childId, UUID learningObjectiveId, int sufficientEvidence) {
        List<LearningObjectiveSkill> skillMappings = learningObjectiveSkillRepository.findByLearningObjectiveId(learningObjectiveId);

        int minEvidence = skillMappings.isEmpty() ? 0 : Integer.MAX_VALUE;
        List<BigDecimal> observed = new java.util.ArrayList<>();
        List<BigDecimal> sufficientlyEvidenced = new java.util.ArrayList<>();
        for (LearningObjectiveSkill mapping : skillMappings) {
            LearnerSkillState skill = skillStateRepository.findByChildIdAndSkillId(childId, mapping.getSkillId()).orElse(null);
            int evidenceCount = skill == null ? 0 : skill.getEvidenceCount();
            minEvidence = Math.min(minEvidence, evidenceCount);
            if (evidenceCount > 0) {
                observed.add(skill.getMasteryProbability());
            }
            if (evidenceCount > 0 && evidenceCount >= sufficientEvidence) {
                sufficientlyEvidenced.add(skill.getMasteryProbability());
            }
        }
        return new ObjectiveEvidence(skillMappings.size(), observed.size(), sufficientlyEvidenced.size(), minEvidence,
                mean(observed), mean(sufficientlyEvidenced));
    }

    /**
     * One objective's learning state, dimension by dimension. A mapped skill without
     * evidence is "not assessed": it lowers {@code skillsObserved} (coverage) and
     * {@code minEvidenceCount}, but never contributes a made-up 0 to a mastery mean.
     *
     * @param observedMastery              mean mastery over skills with any evidence; empty if none
     * @param sufficientlyEvidencedMastery mean mastery over skills with sufficient evidence; empty if none
     */
    public record ObjectiveEvidence(int skillsMapped, int skillsObserved, int skillsSufficientlyEvidenced,
                                     int minEvidenceCount, Optional<BigDecimal> observedMastery,
                                     Optional<BigDecimal> sufficientlyEvidencedMastery) {

        public boolean everySkillObserved() {
            return skillsMapped > 0 && skillsObserved == skillsMapped;
        }
    }

    private void recalculateObjectiveState(UUID childId, UUID learningObjectiveId) {
        // Battle 5C: the rollup averages only the skills that have evidence - a mapped
        // skill never practised is unknown, not 0. Averaging in zeros made a child
        // with perfect evidence on 1 of 4 skills read as 0.25 ("still developing"),
        // i.e. "not assessed" became "failed". Coverage is kept separate (see
        // ObjectiveEvidence), and MASTERED requires every skill to be observed.
        ObjectiveEvidence evidence = objectiveEvidence(childId, learningObjectiveId, 1);
        if (evidence.observedMastery().isEmpty()) {
            return;
        }

        LearnerObjectiveState objectiveState = objectiveStateRepository.findByChildIdAndLearningObjectiveId(childId, learningObjectiveId)
                .orElseGet(() -> objectiveStateRepository.save(LearnerObjectiveState.initial(childId, learningObjectiveId)));
        objectiveState.applyEvidenceRollup(evidence.observedMastery().get(), evidence.everySkillObserved(),
                properties.objectiveMasteryThreshold());
    }

    private static Optional<BigDecimal> mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(total.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP));
    }

    /**
     * {@code learningRate / (1 - (1 - learningRate)^n)} for the n-th observation - see
     * the class javadoc. 1 for the first answer (the estimate becomes that answer),
     * then falling toward {@code learningRate}.
     */
    private BigDecimal stepSize(int evidenceCount) {
        BigDecimal learningRate = properties.learningRate();
        Assert.isTrue(learningRate != null && learningRate.signum() > 0 && learningRate.compareTo(BigDecimal.ONE) <= 0,
                "platform.mastery.learning-rate must be in (0, 1]");
        BigDecimal weightNotYetObserved = BigDecimal.ONE.subtract(learningRate).pow(evidenceCount, MathContext.DECIMAL64);
        return learningRate.divide(BigDecimal.ONE.subtract(weightNotYetObserved), MathContext.DECIMAL64);
    }

    private static BigDecimal clamp(BigDecimal value) {
        if (value.signum() < 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

}