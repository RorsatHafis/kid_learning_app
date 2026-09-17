package com.platform.learner.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivitySkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.knowledge.entity.SkillPrerequisite;
import com.platform.knowledge.repository.SkillPrerequisiteRepository;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.service.LearningPathService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Answers "what should this learner do next" (Section: Adaptive Engine) by reading
 * {@link LearnerSkillState} - already updated per-answer by {@link MasteryEngine},
 * wired in earlier this session - and inserting one {@link LearningPathItemSource#ADAPTIVE}
 * item onto the child's LearningPath. Deterministic and rule-based throughout, no ML
 * or AI call anywhere in this class, matching Section: "Do not make this AI-based."
 *
 * <p>Rules (thresholds are class constants, not yet promoted to configuration
 * properties the way {@code MasteryProperties} is for MasteryEngine - a reasonable
 * next step, not done here to keep this change bounded):
 * <ul>
 *   <li>mastery &lt; {@link #LOW_MASTERY_THRESHOLD}: REINFORCEMENT - target a
 *       prerequisite skill if the skill has one recorded, otherwise the same skill
 *       again, and prefer the lowest-difficulty available activity.</li>
 *   <li>mastery &gt;= {@link #HIGH_MASTERY_THRESHOLD} (and evidenceCount indicates
 *       real confidence, not a lucky first answer): CHALLENGE - same skill, prefer
 *       the highest-difficulty available activity.</li>
 *   <li>otherwise: CONTINUE - same skill, any published activity.</li>
 * </ul>
 * "Repeated errors" (Section: Adaptive Engine) is deliberately not a separate signal
 * here: the EWMA update in MasteryEngine already weights recent evidence more than
 * old evidence (see its class javadoc), so a recent run of misses already pulls
 * mastery below {@link #LOW_MASTERY_THRESHOLD} on its own - a second, independent
 * "recent streak" check would be redundant with what the mastery number already
 * encodes, not a genuinely new signal.
 */
@Service
public class AdaptiveEngine {

    private static final BigDecimal LOW_MASTERY_THRESHOLD = BigDecimal.valueOf(0.4);
    private static final BigDecimal HIGH_MASTERY_THRESHOLD = BigDecimal.valueOf(0.8);
    private static final int MIN_EVIDENCE_FOR_CHALLENGE = 3;

    private final LearnerSkillStateRepository skillStateRepository;
    private final SkillPrerequisiteRepository skillPrerequisiteRepository;
    private final ActivitySkillRepository activitySkillRepository;
    private final ActivityVersionRepository activityVersionRepository;
    private final LearningPathService learningPathService;

    public AdaptiveEngine(LearnerSkillStateRepository skillStateRepository,
                           SkillPrerequisiteRepository skillPrerequisiteRepository,
                           ActivitySkillRepository activitySkillRepository,
                           ActivityVersionRepository activityVersionRepository,
                           LearningPathService learningPathService) {
        this.skillStateRepository = skillStateRepository;
        this.skillPrerequisiteRepository = skillPrerequisiteRepository;
        this.activitySkillRepository = activitySkillRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.learningPathService = learningPathService;
    }

    public enum Direction {
        REINFORCEMENT,
        CONTINUE,
        CHALLENGE
    }

    public record Recommendation(Direction direction, UUID targetSkillId, LearningPathItem insertedItem) {
    }

    /**
     * Compatibility overload for callers that do not have a specific just-completed
     * activity version to exclude.
     */
    public Optional<Recommendation> recommendNext(UUID childId, UUID skillId, UUID learningPathId) {
        return recommendNext(childId, skillId, learningPathId, null);
    }

    /**
     * Decides and immediately applies an adaptive step for one skill. CONTINUE returns
     * empty so the existing curriculum LearningPath remains the normal progression.
     * REINFORCEMENT and CHALLENGE insert an ADAPTIVE item only when a meaningful
     * published activity is available.
     */
    @Transactional
    public Optional<Recommendation> recommendNext(UUID childId, UUID skillId, UUID learningPathId,
                                                   UUID justCompletedActivityVersionId) {
        Optional<LearnerSkillState> maybeState = skillStateRepository.findByChildIdAndSkillId(childId, skillId);
        if (maybeState.isEmpty()) {
            return Optional.empty();
        }

        Direction direction = decideDirection(maybeState.get());
        if (direction == Direction.CONTINUE) {
            return Optional.empty();
        }

        UUID targetSkillId = direction == Direction.REINFORCEMENT
                ? firstPrerequisiteOrSelf(skillId)
                : skillId;

        List<LearningPathItem> pathItems = learningPathService.listItems(learningPathId);
        Set<UUID> completedActivityVersionIds = pathItems.stream()
                .filter(item -> item.getStatus() == LearningPathItemStatus.COMPLETED)
                .map(LearningPathItem::getActivityVersionId)
                .collect(java.util.stream.Collectors.toSet());

        Optional<ActivityVersion> candidate = pickActivity(
                targetSkillId, direction, justCompletedActivityVersionId, completedActivityVersionIds);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        // A recommendation endpoint is intentionally retryable.  Do not append a
        // second copy of an activity that is already waiting on this same path;
        // return that existing placement instead.  Completed placements are never
        // reused, so a high-mastery learner cannot be sent back to an activity they
        // have already finished just because the endpoint was called again.
        Optional<LearningPathItem> existingPlacement = pathItems.stream()
                .filter(item -> item.getActivityVersionId().equals(candidate.get().getId()))
                .filter(item -> item.getStatus() == LearningPathItemStatus.PENDING
                        || item.getStatus() == LearningPathItemStatus.IN_PROGRESS)
                .findFirst();

        if (existingPlacement.isPresent()) {
            return Optional.of(new Recommendation(direction, targetSkillId, existingPlacement.get()));
        }

        LearningPathItem inserted = learningPathService.appendItem(
                learningPathId, candidate.get().getId(), LearningPathItemSource.ADAPTIVE);

        return Optional.of(new Recommendation(direction, targetSkillId, inserted));
    }

    private Direction decideDirection(LearnerSkillState state) {
        if (state.getMasteryProbability().compareTo(LOW_MASTERY_THRESHOLD) < 0) {
            return Direction.REINFORCEMENT;
        }
        if (state.getMasteryProbability().compareTo(HIGH_MASTERY_THRESHOLD) >= 0
                && state.getEvidenceCount() >= MIN_EVIDENCE_FOR_CHALLENGE) {
            return Direction.CHALLENGE;
        }
        return Direction.CONTINUE;
    }

    /** The skill's first recorded prerequisite, or the skill itself if it has none. */
    private UUID firstPrerequisiteOrSelf(UUID skillId) {
        List<SkillPrerequisite> prerequisites = skillPrerequisiteRepository.findBySkillId(skillId);
        return prerequisites.isEmpty() ? skillId : prerequisites.get(0).getPrerequisiteSkillId();
    }

    private Optional<ActivityVersion> pickActivity(UUID skillId, Direction direction,
                                                    UUID excludeActivityVersionId,
                                                    Set<UUID> completedActivityVersionIds) {
        List<ActivityVersion> publishedCandidates = activitySkillRepository.findBySkillId(skillId).stream()
                .map(ActivitySkill::getActivityId)
                .distinct()
                .map(activityId -> activityVersionRepository
                        .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(
                                activityId, PublicationStatus.PUBLISHED))
                .flatMap(Optional::stream)
                .toList();

        List<ActivityVersion> uncompletedCandidates = publishedCandidates.stream()
                .filter(version -> !completedActivityVersionIds.contains(version.getId()))
                .toList();

        if (uncompletedCandidates.isEmpty()) {
            return Optional.empty();
        }

        Comparator<ActivityVersion> byDifficultyNullsLast =
                Comparator.comparing(ActivityVersion::getDifficultyLevel,
                        Comparator.nullsLast(Comparator.naturalOrder()));

        return switch (direction) {
            case REINFORCEMENT -> uncompletedCandidates.stream()
                    .filter(version -> excludeActivityVersionId == null || !version.getId().equals(excludeActivityVersionId))
                    .min(byDifficultyNullsLast);
            case CHALLENGE -> {
                // A challenge must actually be harder than the activity just completed.
                // Without this bound, reaching the hardest tier could make the engine
                // recommend an easier activity, creating a high-mastery loop between
                // difficulty levels instead of a genuine progression step.
                if (excludeActivityVersionId == null) {
                    yield uncompletedCandidates.stream().max(byDifficultyNullsLast);
                }

                Optional<Integer> currentDifficulty = publishedCandidates.stream()
                        .filter(version -> version.getId().equals(excludeActivityVersionId))
                        .map(ActivityVersion::getDifficultyLevel)
                        .findFirst();

                if (currentDifficulty.isEmpty()) {
                    yield Optional.empty();
                }

                yield uncompletedCandidates.stream()
                        .filter(version -> !version.getId().equals(excludeActivityVersionId))
                        .filter(version -> version.getDifficultyLevel() != null
                                && version.getDifficultyLevel() > currentDifficulty.get())
                        .min(Comparator.comparing(ActivityVersion::getDifficultyLevel));
            }
            case CONTINUE -> throw new IllegalStateException(
                    "AdaptiveEngine.pickActivity must never be called for CONTINUE");
        };
    }

}
