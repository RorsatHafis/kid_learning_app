package com.platform.review.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivitySkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.service.LearningPathService;
import com.platform.review.entity.ReviewAttempt;
import com.platform.review.entity.ReviewItem;
import com.platform.review.entity.ReviewItemStatus;
import com.platform.review.entity.ReviewSchedule;
import com.platform.review.repository.ReviewAttemptRepository;
import com.platform.review.repository.ReviewItemRepository;
import com.platform.review.repository.ReviewScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Section 14: "YulMe should identify skills that need reinforcement... based on
 * learner evidence... deterministic scheduling is sufficient." No AI anywhere in
 * this class. Deliberately separate from {@link com.platform.learner.service.AdaptiveEngine}
 * even though both read {@link LearnerSkillState} and both insert
 * {@code LearningPathItem}s - the Adaptive Engine answers "what should this learner
 * practice right now, given where they are"; Smart Review answers a different
 * question, "what has this learner already been evidenced on that's now at risk of
 * being forgotten and due for spaced reinforcement" - distinct product concepts that
 * happen to share the LearningPathItem placement mechanism
 * ({@link LearningPathItemSource#REVIEW} instead of {@code ADAPTIVE}), not the same
 * decision.
 */
@Service
public class SmartReviewService {

    /** Mastery below this, with enough evidence to trust the number, is what "needs reinforcement" means here. */
    private static final BigDecimal REVIEW_TRIGGER_THRESHOLD = BigDecimal.valueOf(0.5);
    private static final int MIN_EVIDENCE_FOR_REVIEW = 2;

    private final ReviewItemRepository reviewItemRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final ReviewAttemptRepository reviewAttemptRepository;
    private final LearnerSkillStateRepository skillStateRepository;
    private final ActivitySkillRepository activitySkillRepository;
    private final ActivityVersionRepository activityVersionRepository;
    private final LearningPathService learningPathService;

    public SmartReviewService(ReviewItemRepository reviewItemRepository, ReviewScheduleRepository reviewScheduleRepository,
                               ReviewAttemptRepository reviewAttemptRepository,
                               LearnerSkillStateRepository skillStateRepository,
                               ActivitySkillRepository activitySkillRepository,
                               ActivityVersionRepository activityVersionRepository,
                               LearningPathService learningPathService) {
        this.reviewItemRepository = reviewItemRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.reviewAttemptRepository = reviewAttemptRepository;
        this.skillStateRepository = skillStateRepository;
        this.activitySkillRepository = activitySkillRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.learningPathService = learningPathService;
    }

    /**
     * Re-evaluates one (child, skill) against current mastery: flags it for review
     * (creating or reactivating its {@link ReviewItem}, due immediately) if mastery
     * is low with enough evidence to trust the number; retires an existing ACTIVE
     * flag if mastery has since recovered. Safe to call after every piece of new
     * evidence for a skill - idempotent either way (reusing the existing row via the
     * (child_id, skill_id) unique constraint, never duplicating it).
     */
    @Transactional
    public Optional<ReviewItem> evaluateAndSchedule(UUID childId, UUID skillId) {
        Optional<LearnerSkillState> maybeState = skillStateRepository.findByChildIdAndSkillId(childId, skillId);
        Optional<ReviewItem> existing = reviewItemRepository.findByChildIdAndSkillId(childId, skillId);

        boolean needsReview = maybeState.isPresent()
                && maybeState.get().getEvidenceCount() >= MIN_EVIDENCE_FOR_REVIEW
                && maybeState.get().getMasteryProbability().compareTo(REVIEW_TRIGGER_THRESHOLD) < 0;

        if (!needsReview) {
            existing.filter(item -> item.getStatus() == ReviewItemStatus.ACTIVE).ifPresent(ReviewItem::retire);
            return Optional.empty();
        }

        ReviewItem item = existing.orElseGet(() -> reviewItemRepository.save(ReviewItem.create(childId, skillId)));
        if (item.getStatus() == ReviewItemStatus.RETIRED) {
            item.reactivate();
        }

        reviewScheduleRepository.findByReviewItemId(item.getId())
                .orElseGet(() -> reviewScheduleRepository.save(ReviewSchedule.initial(item.getId(), Instant.now())));

        return Optional.of(item);
    }

    /**
     * Finds this child's earliest-due ACTIVE review (schedule's {@code due_at} not
     * in the future), places one REVIEW-sourced {@link LearningPathItem} for it -
     * the lowest-difficulty published activity tagged with the review's skill, same
     * picking rule as AdaptiveEngine's reinforcement case - and returns it. Empty if
     * nothing is due, or if nothing due has a published activity to place yet.
     */
    @Transactional
    public Optional<LearningPathItem> insertDueReviewOnPath(UUID childId, UUID learningPathId) {
        Instant now = Instant.now();

        // Repeated calls after the same completion are expected (browser retries,
        // refreshes, and idempotent client recovery).  Once a review item is already
        // waiting on this path, it is the review that should be completed; creating
        // another pending REVIEW item would make the path grow on every retry.
        Optional<LearningPathItem> pendingReview = learningPathService.listItems(learningPathId).stream()
                .filter(item -> item.getSource() == LearningPathItemSource.REVIEW)
                .filter(item -> item.getStatus() == LearningPathItemStatus.PENDING
                        || item.getStatus() == LearningPathItemStatus.IN_PROGRESS)
                .findFirst();
        if (pendingReview.isPresent()) {
            return pendingReview;
        }

        List<ReviewItem> active = reviewItemRepository.findByChildIdAndStatus(childId, ReviewItemStatus.ACTIVE);

        List<ReviewItem> due = active.stream()
                .filter(item -> reviewScheduleRepository.findByReviewItemId(item.getId())
                        .map(schedule -> !schedule.getDueAt().isAfter(now))
                        .orElse(false))
                .sorted(Comparator.comparing(item -> reviewScheduleRepository.findByReviewItemId(item.getId())
                        .map(ReviewSchedule::getDueAt).orElse(Instant.MAX)))
                .toList();

        for (ReviewItem item : due) {
            Optional<ActivityVersion> candidate = pickActivityForSkill(item.getSkillId());
            if (candidate.isPresent()) {
                LearningPathItem inserted = learningPathService.appendItem(
                        learningPathId, candidate.get().getId(), LearningPathItemSource.REVIEW);
                return Optional.of(inserted);
            }
        }

        return Optional.empty();
    }

    /**
     * Records the outcome of an attempt that was itself a review activity, applies
     * the SM-2-style reschedule, and retires the ReviewItem if the child has now
     * shown enough success on it. Idempotent on {@code activityAttemptId}
     * ({@code uq_review_attempts_activity_attempt}, V18) - a retried/duplicate call
     * for the same attempt returns the already-recorded {@link ReviewAttempt}
     * instead of writing a second one.
     */
    @Transactional
    public ReviewAttempt recordOutcome(UUID reviewItemId, UUID activityAttemptId, boolean successful) {
        Optional<ReviewAttempt> existing = reviewAttemptRepository.findByActivityAttemptId(activityAttemptId);
        if (existing.isPresent()) {
            return existing.get();
        }

        ReviewSchedule schedule = reviewScheduleRepository.findByReviewItemId(reviewItemId)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewSchedule for review item", reviewItemId));
        schedule.recordOutcome(successful, Instant.now());

        return reviewAttemptRepository.save(ReviewAttempt.record(reviewItemId, activityAttemptId, successful));
    }

    /**
     * Same as {@link #recordOutcome(UUID, UUID, boolean)}, but looked up by
     * (childId, skillId) - the shape the caller actually has after deriving a skill
     * from a completed attempt's activity, rather than an already-known
     * reviewItemId. Throws {@link ResourceNotFoundException} if this (child, skill)
     * has no ReviewItem at all - a caller should only reach this once it already
     * knows the just-completed attempt's LearningPathItem had
     * {@code source == REVIEW}, so a missing ReviewItem at that point indicates a
     * genuine inconsistency, not a normal "nothing to do" case.
     */
    @Transactional
    public ReviewAttempt recordOutcomeForSkill(UUID childId, UUID skillId, UUID activityAttemptId, boolean successful) {
        ReviewItem item = reviewItemRepository.findByChildIdAndSkillId(childId, skillId)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewItem for child/skill", skillId));
        return recordOutcome(item.getId(), activityAttemptId, successful);
    }

    @Transactional(readOnly = true)
    public List<ReviewItem> listActive(UUID childId) {
        return reviewItemRepository.findByChildIdAndStatus(childId, ReviewItemStatus.ACTIVE);
    }

    private Optional<ActivityVersion> pickActivityForSkill(UUID skillId) {
        return activitySkillRepository.findBySkillId(skillId).stream()
                .map(ActivitySkill::getActivityId)
                .distinct()
                .map(activityId -> activityVersionRepository
                        .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(activityId, PublicationStatus.PUBLISHED))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(ActivityVersion::getDifficultyLevel, Comparator.nullsLast(Comparator.naturalOrder())));
    }

}
