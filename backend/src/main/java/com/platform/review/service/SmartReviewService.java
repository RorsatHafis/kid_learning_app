package com.platform.review.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.service.LearningPathService;
import com.platform.learning.service.PathContentScope;
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
    private final ActivityVersionSkillRepository activitySkillRepository;
    private final ActivityVersionRepository activityVersionRepository;
    private final LearningPathService learningPathService;
    private final PathContentScope pathContentScope;

    public SmartReviewService(ReviewItemRepository reviewItemRepository, ReviewScheduleRepository reviewScheduleRepository,
                               ReviewAttemptRepository reviewAttemptRepository,
                               LearnerSkillStateRepository skillStateRepository,
                               ActivityVersionSkillRepository activitySkillRepository,
                               ActivityVersionRepository activityVersionRepository,
                               LearningPathService learningPathService, PathContentScope pathContentScope) {
        this.reviewItemRepository = reviewItemRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.reviewAttemptRepository = reviewAttemptRepository;
        this.skillStateRepository = skillStateRepository;
        this.activitySkillRepository = activitySkillRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.learningPathService = learningPathService;
        this.pathContentScope = pathContentScope;
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

        boolean needsReview = maybeState.isPresent()
                && maybeState.get().getEvidenceCount() >= MIN_EVIDENCE_FOR_REVIEW
                && maybeState.get().getMasteryProbability().compareTo(REVIEW_TRIGGER_THRESHOLD) < 0;

        // Battle 5D (N2): create-if-absent is decided by the database, then the row is
        // locked, so concurrent evaluations of one (child, skill) converge on a single
        // item and apply their status/schedule changes one at a time.
        if (needsReview) {
            reviewItemRepository.insertIfAbsent(UUID.randomUUID(), childId, skillId);
        }
        Optional<ReviewItem> existing = reviewItemRepository.findByChildIdAndSkillIdForUpdate(childId, skillId);

        if (!needsReview) {
            existing.filter(item -> item.getStatus() == ReviewItemStatus.ACTIVE).ifPresent(ReviewItem::retire);
            return Optional.empty();
        }

        ReviewItem item = existing.orElseThrow(() -> new IllegalStateException(
                "Review item for child %s, skill %s missing right after insert-if-absent".formatted(childId, skillId)));
        if (item.getStatus() == ReviewItemStatus.RETIRED) {
            item.reactivate();
        }

        // Serialized by the item lock above; uq_review_schedules_review_item is the backstop.
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
        return insertDueReviewOnPath(childId, learningPathId, null);
    }

    /**
     * The same placement, for due reviews of {@code skills} only (null: any skill). X25: used when a
     * path has nothing waiting, so a review lands on a path whose curriculum teaches its skill.
     */
    @Transactional
    public Optional<LearningPathItem> insertDueReviewOnPath(UUID childId, UUID learningPathId, java.util.Set<UUID> skills) {
        Instant now = Instant.now();

        // Repeated calls after the same completion are expected (browser retries,
        // refreshes, and idempotent client recovery).  Once a review item is already
        // waiting on this path, it is the review that should be completed; creating
        // another pending REVIEW item would make the path grow on every retry.
        // Battle 5A: that check only holds under concurrency if it runs under the path
        // lock - otherwise two simultaneous calls both see "no pending review" and both
        // append one. The lock is held until this transaction commits.
        learningPathService.lockPath(learningPathId);
        Optional<LearningPathItem> pendingReview = pendingReviewOn(learningPathId);
        if (pendingReview.isPresent()) {
            return pendingReview;
        }

        List<ReviewItem> active = reviewItemRepository.findByChildIdAndStatus(childId, ReviewItemStatus.ACTIVE);

        List<ReviewItem> due = active.stream()
                .filter(item -> skills == null || skills.contains(item.getSkillId()))
                .filter(item -> reviewScheduleRepository.findByReviewItemId(item.getId())
                        .map(schedule -> !schedule.getDueAt().isAfter(now))
                        .orElse(false))
                .sorted(Comparator.comparing(item -> reviewScheduleRepository.findByReviewItemId(item.getId())
                        .map(ReviewSchedule::getDueAt).orElse(Instant.MAX)))
                .toList();

        PathContentScope.Scope scope = pathContentScope.forPath(learningPathId);
        for (ReviewItem item : due) {
            Optional<ActivityVersion> candidate = pickActivityForSkill(item.getSkillId(), scope);
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
        ReviewItem item = reviewItemRepository.findByChildIdAndSkillIdForUpdate(childId, skillId)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewItem for child/skill", skillId));
        return recordOutcome(item.getId(), activityAttemptId, successful);
    }

    /**
     * Battle 5D: records the outcome of a completed REVIEW placement, on the server as
     * part of completion. A REVIEW path item does not record which review item it was
     * placed for, so this uses the child's review item for one of the completed
     * activity's skills: ACTIVE before RETIRED, then earliest due (the order
     * {@link #insertDueReviewOnPath} places reviews in), then skill id. That is
     * deterministic and, for a single-skill activity, exactly the one it was placed for.
     *
     * <p>Returns empty, recording nothing, when none of those skills has a review item.
     * That only happens if the activity's skill tags changed after the review was
     * placed. The review then stays due and is placed again, so nothing is lost.
     */
    @Transactional
    public Optional<ReviewAttempt> recordReviewCompletion(UUID childId, List<UUID> skillIdsInLockOrder,
                                                          UUID activityAttemptId, boolean successful) {
        List<ReviewItem> candidates = skillIdsInLockOrder.stream()
                .map(skillId -> reviewItemRepository.findByChildIdAndSkillIdForUpdate(childId, skillId))
                .flatMap(Optional::stream)
                .toList();
        Comparator<ReviewItem> activeFirstThenEarliestDue = Comparator
                .comparing((ReviewItem item) -> item.getStatus() != ReviewItemStatus.ACTIVE)
                .thenComparing(item -> reviewScheduleRepository.findByReviewItemId(item.getId())
                        .map(ReviewSchedule::getDueAt).orElse(Instant.MAX))
                .thenComparing(ReviewItem::getSkillId);
        return candidates.stream()
                .min(activeFirstThenEarliestDue)
                .map(item -> recordOutcome(item.getId(), activityAttemptId, successful));
    }

    /** Read-only: the REVIEW placement currently waiting on this path, if any. */
    @Transactional(readOnly = true)
    public Optional<LearningPathItem> findPendingReviewOnPath(UUID learningPathId) {
        return pendingReviewOn(learningPathId);
    }

    /** Read-only: the review outcome recorded for this activity attempt, if it was a review. */
    @Transactional(readOnly = true)
    public Optional<ReviewAttempt> findOutcome(UUID activityAttemptId) {
        return reviewAttemptRepository.findByActivityAttemptId(activityAttemptId);
    }

    /** Read-only: this child's review item for the skill, in whatever status. */
    @Transactional(readOnly = true)
    public Optional<ReviewItem> findItem(UUID childId, UUID skillId) {
        return reviewItemRepository.findByChildIdAndSkillId(childId, skillId);
    }

    private Optional<LearningPathItem> pendingReviewOn(UUID learningPathId) {
        return learningPathService.listItems(learningPathId).stream()
                .filter(item -> item.getSource() == LearningPathItemSource.REVIEW)
                .filter(item -> item.getStatus() == LearningPathItemStatus.PENDING
                        || item.getStatus() == LearningPathItemStatus.IN_PROGRESS)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<ReviewItem> listActive(UUID childId) {
        return reviewItemRepository.findByChildIdAndStatus(childId, ReviewItemStatus.ACTIVE);
    }

    /** N7: only content the path's scope admits (see {@link PathContentScope}). */
    private Optional<ActivityVersion> pickActivityForSkill(UUID skillId, PathContentScope.Scope scope) {
        return activitySkillRepository.findPublishedPractising(skillId).stream()
                .filter(version -> pathContentScope.admitsActivity(scope, version.getActivityId()))
                .min(Comparator.comparing(ActivityVersion::getDifficultyLevel, Comparator.nullsLast(Comparator.naturalOrder())));
    }

}
