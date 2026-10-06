package com.platform.learning.service;

import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersionSkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.QuestionVersionSkill;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.QuestionVersionSkillRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.learner.entity.AdaptationDecision;
import com.platform.learner.entity.AdaptationDecisionType;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.AdaptationDecisionRepository;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learner.service.AdaptiveEngine;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.repository.AnswerRecordRepository;
import com.platform.review.entity.ReviewItemStatus;
import com.platform.review.service.SmartReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Battle 5D: what completing an activity means for the child's learning state beyond
 * the attempt itself, decided on the server as part of completion. It no longer
 * depends on the client calling {@code /adaptive-next} and {@code /smart-review-check}
 * afterwards (L8): a closed browser, a lost network or a crashed page can't leave a
 * completed attempt without its review and adaptive state.
 *
 * <p>Runs inside the completion transaction ({@link Propagation#MANDATORY}), so
 * completion and its decisions commit together or not at all. Completion runs this
 * exactly once per attempt: it holds the attempt's row lock and returns early for an
 * already-COMPLETED attempt. {@code uq_adaptation_decisions_activity_attempt} (V36)
 * is the database backstop.
 *
 * <p>Steps, in this order (they were previously two independent client calls racing
 * each other):
 * <ol>
 *   <li>if the completed item was a REVIEW placement, record its outcome;</li>
 *   <li>re-evaluate Smart Review for every skill the activity exercises;</li>
 *   <li>make and record one adaptive decision;</li>
 *   <li>place a due review, if any.</li>
 * </ol>
 *
 * <p>Evidence and mastery are not recomputed here: they were recorded, answer by
 * answer, when each answer was submitted.
 *
 * <p>Free practice (no learning path item) makes no path decisions - those place items on a
 * path, and free practice has none - but Smart Review re-evaluates its skills (X26).
 */
@Service
public class PostCompletionService {

    /**
     * L7 (multi-skill activities), provisional: one adaptive decision per completion,
     * governed by the exercised skill with the lowest mastery (ties by skill id). The
     * choice is deterministic and conservative - a child weak in any skill the
     * activity exercised is never challenged on its strength. Whether multi-skill
     * activities should instead drive one decision per skill is an open product
     * decision; single-skill activities (all seeded content) are unaffected.
     */
    static final String GOVERNING_SKILL_RULE = "LOWEST_MASTERY_EXERCISED_SKILL";

    private final ActivityVersionRepository activityVersionRepository;
    private final ActivityVersionSkillRepository activitySkillRepository;
    private final ActivityItemRepository activityItemRepository;
    private final QuestionVersionRepository questionVersionRepository;
    private final QuestionVersionSkillRepository questionSkillRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final LearnerSkillStateRepository skillStateRepository;
    private final AdaptationDecisionRepository adaptationDecisionRepository;
    private final LearningPathService learningPathService;
    private final AdaptiveEngine adaptiveEngine;
    private final SmartReviewService smartReviewService;

    public PostCompletionService(ActivityVersionRepository activityVersionRepository,
                                 ActivityVersionSkillRepository activitySkillRepository,
                                 ActivityItemRepository activityItemRepository,
                                 QuestionVersionRepository questionVersionRepository,
                                 QuestionVersionSkillRepository questionSkillRepository,
                                 AnswerRecordRepository answerRecordRepository,
                                 LearnerSkillStateRepository skillStateRepository,
                                 AdaptationDecisionRepository adaptationDecisionRepository,
                                 LearningPathService learningPathService, AdaptiveEngine adaptiveEngine,
                                 SmartReviewService smartReviewService) {
        this.activityVersionRepository = activityVersionRepository;
        this.activitySkillRepository = activitySkillRepository;
        this.activityItemRepository = activityItemRepository;
        this.questionVersionRepository = questionVersionRepository;
        this.questionSkillRepository = questionSkillRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.skillStateRepository = skillStateRepository;
        this.adaptationDecisionRepository = adaptationDecisionRepository;
        this.learningPathService = learningPathService;
        this.adaptiveEngine = adaptiveEngine;
        this.smartReviewService = smartReviewService;
    }

    /** Applies the post-completion decisions for an attempt that has just become COMPLETED. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(ActivityAttempt attempt) {
        if (attempt.getStatus() != ActivityAttemptStatus.COMPLETED) {
            return;
        }
        if (attempt.getLearningPathItemId() == null) {
            // Free practice (X24: a book played again): no path, so no adaptive decision and nothing placed -
            // but its answers are evidence, so Smart Review re-evaluates the skills it exercised, as after any
            // evidence. A due review is placed on a path later (the completion flow, or X25 when nothing waits).
            exercisedSkills(attempt.getActivityVersionId())
                    .forEach(skillId -> smartReviewService.evaluateAndSchedule(attempt.getChildId(), skillId));
            return;
        }
        UUID childId = attempt.getChildId();
        LearningPathItem item = learningPathService.getItem(attempt.getLearningPathItemId());
        List<UUID> skillIds = exercisedSkills(attempt.getActivityVersionId());

        if (item.getSource() == LearningPathItemSource.REVIEW) {
            smartReviewService.recordReviewCompletion(childId, skillIds, attempt.getId(), wasSuccessful(attempt));
        }
        skillIds.forEach(skillId -> smartReviewService.evaluateAndSchedule(childId, skillId));
        recordAdaptiveDecision(attempt, item, skillIds);
        smartReviewService.insertDueReviewOnPath(childId, item.getLearningPathId());
    }

    private void recordAdaptiveDecision(ActivityAttempt attempt, LearningPathItem item, List<UUID> skillIds) {
        UUID childId = attempt.getChildId();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("exercisedSkillIds", skillIds.stream().map(UUID::toString).toList());
        context.put("governingSkillRule", GOVERNING_SKILL_RULE);

        Optional<LearnerSkillState> governing = skillIds.stream()
                .map(skillId -> skillStateRepository.findByChildIdAndSkillId(childId, skillId))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(LearnerSkillState::getMasteryProbability)
                        .thenComparing(LearnerSkillState::getSkillId));
        if (governing.isEmpty()) {
            adaptationDecisionRepository.save(AdaptationDecision.forCompletion(childId, attempt.getId(),
                    AdaptationDecisionType.CONTINUE, context, null, null,
                    skillIds.isEmpty() ? "The activity is not tagged with any skill" : "No evidence yet for the activity's skills"));
            return;
        }

        AdaptiveEngine.Decision decision = adaptiveEngine.decide(
                childId, governing.get().getSkillId(), item.getLearningPathId(), attempt.getActivityVersionId());
        context.put("governingSkillId", decision.skillId().toString());
        context.put("targetSkillId", decision.targetSkillId().toString());
        context.put("direction", decision.direction().name());

        AdaptationDecisionType type = switch (decision.direction()) {
            case REINFORCEMENT -> AdaptationDecisionType.REMEDIATE;
            case CHALLENGE -> AdaptationDecisionType.INCREASE_DIFFICULTY;
            case CONTINUE -> AdaptationDecisionType.CONTINUE;
        };
        String reason = decision.direction() == AdaptiveEngine.Direction.CONTINUE
                ? "Mastery on the governing skill calls for neither reinforcement nor challenge"
                : decision.placedItem().isPresent()
                        ? "The recommended activity is on the learning path"
                        : "No published activity available for the target skill";
        adaptationDecisionRepository.save(AdaptationDecision.forCompletion(childId, attempt.getId(), type, context,
                decision.placedItem().map(LearningPathItem::getActivityVersionId).orElse(null),
                decision.placedItem().map(LearningPathItem::getId).orElse(null), reason));
    }

    /**
     * The skills an activity version exercises: the version's own skill tags plus the tags of
     * the question versions it pins (evidence is recorded per question version skill, PD-08).
     * Sorted, so every caller locks review items in the same order.
     */
    List<UUID> exercisedSkills(UUID activityVersionId) {
        activityVersionRepository.findById(activityVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityVersion", activityVersionId));
        TreeSet<UUID> skillIds = new TreeSet<>();
        activitySkillRepository.findByActivityVersionIdOrderByCreatedAtAscIdAsc(activityVersionId).stream()
                .map(ActivityVersionSkill::getSkillId)
                .forEach(skillIds::add);
        for (ActivityItem activityItem : activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId)) {
            questionSkillRepository.findByQuestionVersionIdOrderByCreatedAtAscIdAsc(activityItem.getQuestionVersionId()).stream()
                    .map(QuestionVersionSkill::getSkillId)
                    .forEach(skillIds::add);
        }
        return List.copyOf(skillIds);
    }

    /** Same rule the review outcome always used: at least half of the answers correct. */
    private boolean wasSuccessful(ActivityAttempt attempt) {
        List<AnswerRecord> answers = answerRecordRepository.findByActivityAttemptId(attempt.getId());
        return !answers.isEmpty() && answers.stream().filter(AnswerRecord::isCorrect).count() * 2 >= answers.size();
    }

    // ------------------------------------------------------------------ read-only views

    /** What the adaptive decision for this attempt placed, if anything (compatibility read for /adaptive-next). */
    @Transactional(readOnly = true)
    public Optional<PlacedAdaptiveStep> findPlacedAdaptiveStep(UUID activityAttemptId) {
        return adaptationDecisionRepository.findByActivityAttemptId(activityAttemptId)
                .filter(decision -> decision.getLearningPathItemId() != null)
                .map(decision -> new PlacedAdaptiveStep(
                        decision.getDecisionType() == AdaptationDecisionType.REMEDIATE ? "REINFORCEMENT" : "CHALLENGE",
                        UUID.fromString(String.valueOf(decision.getContext().get("targetSkillId"))),
                        decision.getLearningPathItemId(), decision.getSelectedActivityVersionId()));
    }

    /** The persisted review state after this attempt (compatibility read for /smart-review-check). */
    @Transactional(readOnly = true)
    public Optional<ReviewState> findReviewState(ActivityAttempt attempt) {
        if (attempt.getStatus() != ActivityAttemptStatus.COMPLETED || attempt.getLearningPathItemId() == null) {
            return Optional.empty();
        }
        Optional<UUID> skillId = adaptationDecisionRepository.findByActivityAttemptId(attempt.getId())
                .map(decision -> decision.getContext().get("governingSkillId"))
                .map(value -> UUID.fromString(String.valueOf(value)))
                .or(() -> exercisedSkills(attempt.getActivityVersionId()).stream().findFirst());
        if (skillId.isEmpty()) {
            return Optional.empty();
        }
        UUID learningPathId = learningPathService.getItem(attempt.getLearningPathItemId()).getLearningPathId();
        Optional<LearningPathItem> pendingReview = smartReviewService.findPendingReviewOnPath(learningPathId);
        return Optional.of(new ReviewState(
                smartReviewService.findOutcome(attempt.getId()).isPresent(),
                smartReviewService.findItem(attempt.getChildId(), skillId.get())
                        .filter(item -> item.getStatus() == ReviewItemStatus.ACTIVE).isPresent(),
                skillId.get(),
                pendingReview.map(LearningPathItem::getId).orElse(null),
                pendingReview.map(LearningPathItem::getActivityVersionId).orElse(null)));
    }

    public record PlacedAdaptiveStep(String direction, UUID targetSkillId, UUID learningPathItemId, UUID activityVersionId) {
    }

    public record ReviewState(boolean reviewOutcomeRecorded, boolean flaggedForReview, UUID skillId,
                              UUID pendingReviewLearningPathItemId, UUID pendingReviewActivityVersionId) {
    }

}
