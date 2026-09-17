package com.platform.learning.service;

import com.platform.child.repository.ChildRepository;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.idempotency.service.IdempotencyOutcome;
import com.platform.common.idempotency.service.IdempotencyService;
import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.repository.ActivityAttemptRepository;
import com.platform.learning.repository.AnswerRecordRepository;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.learning.repository.LearningPathItemRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import com.platform.streak.service.LearnerStreakService;

/**
 * Manages ActivityAttempts and drives answer submission. See {@link IdempotencyService}'s
 * class javadoc for the two-phase begin/write/complete-or-fail contract
 * {@link #submitAnswer} follows, and {@code RegisterAccountService} for the first
 * place that pattern was established in this codebase.
 */
@Service
public class ActivityAttemptService {

    private static final String SUBMIT_ANSWER_OPERATION = "answer-submission";
    private static final Duration SUBMIT_ANSWER_VALIDITY = Duration.ofHours(1);

    private final ActivityAttemptRepository attemptRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final ActivityItemRepository activityItemRepository;
    private final ActivityVersionRepository activityVersionRepository;
    private final ChildRepository childRepository;
    private final IdempotencyService idempotencyService;
    private final AnswerSubmissionWriter answerSubmissionWriter;
    private final LearningPathItemRepository learningPathItemRepository;
    private final LearningPathRepository learningPathRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Autowired(required = false)
    private LearnerStreakService learnerStreakService;

    public ActivityAttemptService(ActivityAttemptRepository attemptRepository, AnswerRecordRepository answerRecordRepository,
                                   ActivityItemRepository activityItemRepository,
                                   ActivityVersionRepository activityVersionRepository, ChildRepository childRepository,
                                   IdempotencyService idempotencyService, AnswerSubmissionWriter answerSubmissionWriter,
                                   LearningPathItemRepository learningPathItemRepository,
                                   LearningPathRepository learningPathRepository, EnrollmentRepository enrollmentRepository) {
        this.attemptRepository = attemptRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.activityItemRepository = activityItemRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.childRepository = childRepository;
        this.idempotencyService = idempotencyService;
        this.answerSubmissionWriter = answerSubmissionWriter;
        this.learningPathItemRepository = learningPathItemRepository;
        this.learningPathRepository = learningPathRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * Starts a new attempt, or returns the existing IN_PROGRESS one for this exact
     * (child, activity version) pair if there is one - handles the double-click /
     * duplicate-start-request case (Section 35) without needing the full
     * IdempotencyService machinery, since "is there already an in-progress attempt"
     * is a natural, directly-queryable check here.
     */
    @Transactional
    public ActivityAttempt startAttempt(UUID childId, UUID activityVersionId, UUID learningPathItemId, UUID learningSessionId) {
        if (!childRepository.existsById(childId)) {
            throw new ResourceNotFoundException("Child", childId);
        }
        requirePublishedActivityVersion(activityVersionId);
        if (learningPathItemId != null) {
            requireLearningPathItemBelongsToChild(childId, activityVersionId, learningPathItemId);
        }

        Optional<ActivityAttempt> inProgress = attemptRepository.findByChildIdAndActivityVersionId(childId, activityVersionId)
                .stream()
                .filter(a -> a.getStatus() == ActivityAttemptStatus.IN_PROGRESS)
                .findFirst();
        if (inProgress.isPresent()) {
            return inProgress.get();
        }

        if (learningPathItemId != null) {
            markPathItemStartedIfPending(learningPathItemId);
        }

        return attemptRepository.save(
                ActivityAttempt.start(childId, activityVersionId, learningPathItemId, learningSessionId, Instant.now()));
    }

    /** Learner attempts may only be created for published activity versions. */
    private void requirePublishedActivityVersion(UUID activityVersionId) {
        ActivityVersion version = activityVersionRepository.findById(activityVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityVersion", activityVersionId));
        if (version.getStatus() != PublicationStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Published ActivityVersion", activityVersionId);
        }
    }

    /**
     * Backend integration gap found while wiring the frontend: {@code start()}/
     * {@code complete()} already existed on {@link LearningPathItem} (see also
     * {@link #markPathItemCompletedIfApplicable}) but nothing ever called them, so
     * an item stayed PENDING forever - which means {@code getNextItem} (the
     * enrollment "next-activity" endpoint) would keep re-returning the exact same
     * item after it was attempted, never advancing, and Adaptive/Review-inserted
     * items would never be reached through that endpoint either. Guarded on
     * current status (only PENDING -&gt; IN_PROGRESS) so re-attempting after an
     * abandon doesn't throw {@code LearningPathItem}'s own transition guard.
     */
    private void markPathItemStartedIfPending(UUID learningPathItemId) {
        LearningPathItem item = learningPathItemRepository.findById(learningPathItemId)
                .orElseThrow(() -> new ResourceNotFoundException("LearningPathItem", learningPathItemId));
        if (item.getStatus() == LearningPathItemStatus.PENDING) {
            item.start();
        }
    }

    /**
     * Known Backend Fix #2 (attempt/learning-path integrity): a client-supplied
     * {@code learningPathItemId} must actually belong to the child starting the
     * attempt, and the activity being attempted must be the one that path item
     * actually points at. Without this, one child could pass another child's path
     * item id (cross-child data access), or an attempt could be recorded against
     * an activity version unrelated to the path item it claims to advance
     * (letting the Adaptive Engine / Smart Review be driven by a mismatched
     * signal). Chain: item -&gt; path -&gt; enrollment -&gt; childId, resolved from
     * persisted state only, never trusted from the request.
     */
    private void requireLearningPathItemBelongsToChild(UUID childId, UUID activityVersionId, UUID learningPathItemId) {
        LearningPathItem item = learningPathItemRepository.findById(learningPathItemId)
                .orElseThrow(() -> new ResourceNotFoundException("LearningPathItem", learningPathItemId));

        LearningPath path = learningPathRepository.findById(item.getLearningPathId())
                .orElseThrow(() -> new ResourceNotFoundException("LearningPath", item.getLearningPathId()));

        Enrollment enrollment = enrollmentRepository.findById(path.getEnrollmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", path.getEnrollmentId()));

        if (!enrollment.getChildId().equals(childId)) {
            throw new AccessDeniedException(
                    "LearningPathItem %s does not belong to child %s".formatted(learningPathItemId, childId));
        }
        if (!item.getActivityVersionId().equals(activityVersionId)) {
            throw new IllegalArgumentException(
                    "ActivityVersion %s does not match the activity version %s carried by learning path item %s"
                            .formatted(activityVersionId, item.getActivityVersionId(), learningPathItemId));
        }
    }

    @Transactional(readOnly = true)
    public ActivityAttempt getAttempt(UUID id) {
        return attemptRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ActivityAttempt", id));
    }

    @Transactional(readOnly = true)
    public List<AnswerRecord> listAnswers(UUID activityAttemptId) {
        return answerRecordRepository.findByActivityAttemptId(activityAttemptId);
    }

    /**
     * Retry-safe completion (Section 41: attempt completion needs idempotency
     * semantics), but via the entity's own state guard rather than the full
     * IdempotencyService pattern: unlike answer submission, completing an
     * already-completed attempt has no new evidence to double-count - the only
     * risk is a confusing false-failure on a client retry, which returning the
     * existing completed attempt instead of throwing avoids directly.
     *
     * <p>Known Backend Fix #1 (server-authoritative score): this method no longer
     * accepts a score from the caller. A client that could submit its own score
     * could report {@code 100} regardless of what it actually answered, which
     * would corrupt the result screen and any parent-facing summary that reads
     * {@code ActivityAttempt.score} - though not mastery/adaptive/review, which
     * were already reading {@link AnswerRecord#isCorrect()} directly and never
     * this field. The score is now always derived from this attempt's own
     * persisted {@link AnswerRecord}s at completion time.
     *
     * <p>Known Backend Fix A (completion/scoring integrity): completion itself is
     * now rejected (see {@link #calculateScore}) unless every required question -
     * every {@link ActivityItem} belonging to this attempt's ActivityVersion - has
     * an AnswerRecord. Previously an attempt with several required questions could
     * be completed, and scored 100%, after a single answer.
     */
    @Transactional
    public ActivityAttempt completeAttempt(UUID activityAttemptId) {
        ActivityAttempt attempt = getAttempt(activityAttemptId);
        if (attempt.getStatus() == ActivityAttemptStatus.COMPLETED) {
            return attempt;
        }
        BigDecimal score = calculateScore(attempt);
        Instant completedAt = Instant.now();
        attempt.complete(completedAt, score);
        if (learnerStreakService != null) learnerStreakService.recordLearning(attempt.getChildId(), completedAt);
        if (attempt.getLearningPathItemId() != null) {
            markPathItemCompletedIfApplicable(attempt.getLearningPathItemId());
        }
        return attempt;
    }

    /** Counterpart to {@link #markPathItemStartedIfPending} - see that method's javadoc for why this needs to happen at all. Guarded the same way: only PENDING/IN_PROGRESS items can be completed (LearningPathItem#complete's own transition guard), so a second, unrelated attempt against an already-COMPLETED item (e.g. free re-practice) is a no-op here rather than a thrown exception. */
    private void markPathItemCompletedIfApplicable(UUID learningPathItemId) {
        LearningPathItem item = learningPathItemRepository.findById(learningPathItemId)
                .orElseThrow(() -> new ResourceNotFoundException("LearningPathItem", learningPathItemId));
        if (item.getStatus() == LearningPathItemStatus.PENDING || item.getStatus() == LearningPathItemStatus.IN_PROGRESS) {
            item.complete(Instant.now());
        }
    }

    /**
     * Known Backend Fix A (completion/scoring integrity): the required questions for
     * an attempt are the {@link ActivityItem}s actually belonging to its
     * ActivityVersion (V21) - never however many answers happen to have been
     * submitted. Completion is rejected (409, {@link IllegalStateException}) until
     * every one of them has an {@link AnswerRecord}; once that holds, the score is
     * server-correct-required-answers / total-required-questions, out of 100 to 2
     * decimal places - never the submitted-answer count as the denominator, and
     * never a client-supplied score/correctness value.
     */
    private BigDecimal calculateScore(ActivityAttempt attempt) {
        List<ActivityItem> requiredItems =
                activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(attempt.getActivityVersionId());
        if (requiredItems.isEmpty()) {
            // No ActivityItem is attached to this ActivityVersion at all, so there is
            // nothing to require and nothing to score - not expected for any real,
            // published content (every seeded ActivityVersion has at least one), but a
            // defensive branch rather than a division by zero if it ever happens.
            return null;
        }

        Map<UUID, AnswerRecord> answersByQuestion = answerRecordRepository.findByActivityAttemptId(attempt.getId())
                .stream()
                // Fix B (duplicate answer integrity) guarantees at most one AnswerRecord
                // per (attempt, question) pair, application- and database-side; the merge
                // function is a defensive "first one wins" rather than an assumption that
                // guarantee can never be violated.
                .collect(Collectors.toMap(AnswerRecord::getQuestionVersionId, answer -> answer, (first, second) -> first));

        List<UUID> unanswered = requiredItems.stream()
                .map(ActivityItem::getQuestionVersionId)
                .filter(questionVersionId -> !answersByQuestion.containsKey(questionVersionId))
                .toList();
        if (!unanswered.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot complete attempt %s: %d of %d required questions have not been answered yet"
                            .formatted(attempt.getId(), unanswered.size(), requiredItems.size()));
        }

        long correctCount = requiredItems.stream()
                .map(ActivityItem::getQuestionVersionId)
                .filter(questionVersionId -> answersByQuestion.get(questionVersionId).isCorrect())
                .count();

        return BigDecimal.valueOf(correctCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(requiredItems.size()), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public ActivityAttempt abandonAttempt(UUID activityAttemptId) {
        ActivityAttempt attempt = getAttempt(activityAttemptId);
        if (attempt.getStatus() == ActivityAttemptStatus.ABANDONED) {
            return attempt;
        }
        attempt.abandon(Instant.now());
        return attempt;
    }

    /**
     * Submits and scores one answer within an attempt. {@code idempotencyKey}
     * should be a client-generated token resent unchanged on retry.
     */
    public AnswerRecord submitAnswer(UUID activityAttemptId, UUID questionVersionId, String submittedAnswer,
                                      Integer timeSpentSeconds, String idempotencyKey) {
        ActivityAttempt attempt = getAttempt(activityAttemptId);
        if (attempt.getStatus() != ActivityAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot submit an answer for attempt %s, which is %s".formatted(activityAttemptId, attempt.getStatus()));
        }

        // Section 25: a request must not submit answer data for a different question -
        // validated against activity_items (V21), never trusted from client input.
        activityItemRepository.findByActivityVersionIdAndQuestionVersionId(attempt.getActivityVersionId(), questionVersionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ActivityItem for this activity version and question version %s".formatted(questionVersionId),
                        attempt.getActivityVersionId()));

        String fingerprint = RequestFingerprint.sha256Hex(
                activityAttemptId + "\u0000" + questionVersionId + "\u0000" + submittedAnswer + "\u0000" + timeSpentSeconds);

        IdempotencyOutcome outcome = idempotencyService.begin(
                attempt.getChildId(), SUBMIT_ANSWER_OPERATION, idempotencyKey, fingerprint, SUBMIT_ANSWER_VALIDITY);

        if (outcome instanceof IdempotencyOutcome.AlreadyCompleted) {
            // IdempotencyRecord has no stored reference to its result (a documented
            // limitation - see IdempotencyService's class javadoc); re-derive it instead.
            // See AnswerRecordRepository's comment for the accepted edge case this creates.
            return answerRecordRepository
                    .findTopByActivityAttemptIdAndQuestionVersionIdOrderByOccurredAtDesc(activityAttemptId, questionVersionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Answer submission for question %s was marked complete but no AnswerRecord exists"
                                    .formatted(questionVersionId)));
        }

        UUID guardId = ((IdempotencyOutcome.Started) outcome).record().getId();

        // Known Backend Fix B (duplicate answer integrity): unlike the writer's other
        // failure modes (unknown question/question version, unscorable question type -
        // both driven by content data that won't change between retries), a duplicate
        // answer IS something a retry with the *same* idempotency key would resolve
        // correctly (it would hit AlreadyCompleted above, never reach here again). So,
        // same reasoning as RegisterAccountService: cache the failure via
        // idempotencyService.fail() so this guard doesn't linger IN_PROGRESS for the
        // full validity window, blocking a legitimate later retry under a fresh key.
        try {
            return answerSubmissionWriter.write(
                    attempt.getChildId(), activityAttemptId, questionVersionId, submittedAnswer, timeSpentSeconds, guardId);
        } catch (IllegalStateException duplicateAnswer) {
            idempotencyService.fail(guardId);
            throw duplicateAnswer;
        }
    }

}