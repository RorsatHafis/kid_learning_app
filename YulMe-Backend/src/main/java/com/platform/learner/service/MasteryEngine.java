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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Phase 1 mastery engine: deterministic, explainable, no ML (Section 16 of the
 * master prompt is explicit that this is preferable to "fake AI" at this stage).
 *
 * Algorithm - EWMA (exponentially weighted moving average) toward observed outcome:
 * <pre>
 *   outcome = 1.0 if correct, 0.0 if incorrect
 *   newMastery = oldMastery + learningRate * (outcome - oldMastery)
 * </pre>
 * This is a standard, well-understood update rule: it moves the estimate toward
 * whatever was just observed, by a fraction controlled by {@code learningRate}
 * (configurable, see MasteryProperties) - never overreacting to a single data point,
 * while naturally weighting recent evidence more than old evidence (each new
 * observation partially "forgets" the accumulated past, which is exactly the
 * recency behavior Section 16 asks for, without needing explicit timestamps in the
 * formula). Confidence scales linearly with evidence_count up to a configured
 * saturation point, matching "small amount of evidence -> confidence remains
 * limited."
 *
 * Objective-level rollup is a simple average of its skills' mastery - a documented,
 * deliberately simple choice; a weighted average (e.g. by skill importance) is a
 * reasonable future refinement, not built speculatively here.
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

        BigDecimal outcome = correct ? BigDecimal.ONE : BigDecimal.ZERO;
        BigDecimal delta = outcome.subtract(state.getMasteryProbability()).multiply(properties.learningRate());
        BigDecimal newMastery = clamp(state.getMasteryProbability().add(delta));

        int newEvidenceCount = state.getEvidenceCount() + 1;
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

    private void recalculateObjectivesForSkill(UUID childId, UUID skillId) {
        List<LearningObjectiveSkill> mappings = learningObjectiveSkillRepository.findBySkillId(skillId);
        for (LearningObjectiveSkill mapping : mappings) {
            recalculateObjectiveState(childId, mapping.getLearningObjectiveId());
        }
    }

    private void recalculateObjectiveState(UUID childId, UUID learningObjectiveId) {
        List<LearningObjectiveSkill> skillMappings = learningObjectiveSkillRepository.findByLearningObjectiveId(learningObjectiveId);
        if (skillMappings.isEmpty()) {
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (LearningObjectiveSkill mapping : skillMappings) {
            BigDecimal skillMastery = skillStateRepository.findByChildIdAndSkillId(childId, mapping.getSkillId())
                    .map(LearnerSkillState::getMasteryProbability)
                    .orElse(BigDecimal.ZERO);
            total = total.add(skillMastery);
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(skillMappings.size()), 4, RoundingMode.HALF_UP);

        LearnerObjectiveState objectiveState = objectiveStateRepository.findByChildIdAndLearningObjectiveId(childId, learningObjectiveId)
                .orElseGet(() -> objectiveStateRepository.save(LearnerObjectiveState.initial(childId, learningObjectiveId)));
        objectiveState.updateMastery(average, properties.objectiveMasteryThreshold());
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