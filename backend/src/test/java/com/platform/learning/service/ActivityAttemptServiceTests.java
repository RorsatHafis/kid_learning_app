package com.platform.learning.service;

import com.platform.child.repository.ChildRepository;
import com.platform.common.idempotency.service.IdempotencyService;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersion;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.repository.ActivityAttemptRepository;
import com.platform.learning.repository.AnswerRecordRepository;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.learning.repository.LearningPathItemRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers mission Known Backend Fix #1 (server-authoritative attempt score) and
 * Known Backend Fix #2 (attempt/learning-path integrity) at the service layer,
 * with every collaborator mocked - the actual multi-child, real-database version
 * of "does the platform behave correctly" already lives in
 * {@link com.platform.learning.GoldenLearningJourneyIntegrationTests}; this class
 * exists to pin down the two specific defect classes the mission calls out, with
 * fast, deterministic, no-database tests.
 */
@ExtendWith(MockitoExtension.class)
class ActivityAttemptServiceTests {

    private static final UUID CHILD_ID = UUID.randomUUID();
    private static final UUID OTHER_CHILD_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_VERSION_ID = UUID.randomUUID();
    private static final UUID OTHER_ACTIVITY_VERSION_ID = UUID.randomUUID();
    private static final UUID LEARNING_PATH_ITEM_ID = UUID.randomUUID();
    private static final UUID LEARNING_PATH_ID = UUID.randomUUID();
    private static final UUID ENROLLMENT_ID = UUID.randomUUID();
    private static final UUID ATTEMPT_ID = UUID.randomUUID();

    @Mock private ActivityAttemptRepository attemptRepository;
    @Mock private AnswerRecordRepository answerRecordRepository;
    @Mock private ActivityItemRepository activityItemRepository;
    @Mock private ActivityVersionRepository activityVersionRepository;
    @Mock private ChildRepository childRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private AnswerSubmissionWriter answerSubmissionWriter;
    @Mock private LearningPathItemRepository learningPathItemRepository;
    @Mock private LearningPathRepository learningPathRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    private ActivityAttemptService service;

    @BeforeEach
    void setUp() {
        service = new ActivityAttemptService(attemptRepository, answerRecordRepository, activityItemRepository,
                activityVersionRepository, childRepository, idempotencyService, answerSubmissionWriter,
                learningPathItemRepository, learningPathRepository, enrollmentRepository);
        lenient().when(activityVersionRepository.findById(ACTIVITY_VERSION_ID))
                .thenReturn(Optional.of(publishedActivityVersion(ACTIVITY_VERSION_ID)));
    }

    // ---- Known Backend Fix #1: server-authoritative score --------------------

    @Test
    void completeAttemptDerivesScoreFromPersistedAnswersRegardlessOfWhatAnyCallerMightWish() {
        ActivityAttempt attempt = ActivityAttempt.start(CHILD_ID, ACTIVITY_VERSION_ID, null, null, Instant.now());
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(withId(attempt, ATTEMPT_ID)));
        // 3 correct out of 4 -> 75.00, and there is no parameter anywhere on this
        // method a caller could use to say otherwise - the fix is structural, not
        // just "validate the number".
        UUID firstQuestion = UUID.randomUUID();
        UUID secondQuestion = UUID.randomUUID();
        UUID thirdQuestion = UUID.randomUUID();
        UUID fourthQuestion = UUID.randomUUID();
        when(activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(ACTIVITY_VERSION_ID))
                .thenReturn(requiredItems(firstQuestion, secondQuestion, thirdQuestion, fourthQuestion));
        when(answerRecordRepository.findByActivityAttemptId(ATTEMPT_ID)).thenReturn(List.of(
                answer(firstQuestion, true), answer(secondQuestion, true), answer(thirdQuestion, true), answer(fourthQuestion, false)));

        ActivityAttempt completed = service.completeAttempt(ATTEMPT_ID);

        assertThat(completed.getStatus()).isEqualTo(ActivityAttemptStatus.COMPLETED);
        assertThat(completed.getScore()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void completeAttemptScoresZeroWhenEveryAnswerWasWrong() {
        ActivityAttempt attempt = ActivityAttempt.start(CHILD_ID, ACTIVITY_VERSION_ID, null, null, Instant.now());
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(withId(attempt, ATTEMPT_ID)));
        UUID firstQuestion = UUID.randomUUID();
        UUID secondQuestion = UUID.randomUUID();
        when(activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(ACTIVITY_VERSION_ID))
                .thenReturn(requiredItems(firstQuestion, secondQuestion));
        when(answerRecordRepository.findByActivityAttemptId(ATTEMPT_ID)).thenReturn(List.of(
                answer(firstQuestion, false), answer(secondQuestion, false)));

        ActivityAttempt completed = service.completeAttempt(ATTEMPT_ID);

        assertThat(completed.getScore()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void completingAnAlreadyCompletedAttemptIsARetrySafeNoOpAndDoesNotRescoreIt() {
        ActivityAttempt attempt = ActivityAttempt.start(CHILD_ID, ACTIVITY_VERSION_ID, null, null, Instant.now());
        attempt.complete(Instant.now(), new BigDecimal("50.00"));
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(withId(attempt, ATTEMPT_ID)));

        ActivityAttempt result = service.completeAttempt(ATTEMPT_ID);

        assertThat(result.getScore()).isEqualByComparingTo(new BigDecimal("50.00"));
        verify(answerRecordRepository, org.mockito.Mockito.never()).findByActivityAttemptId(any());
    }

    @Test
    void completeAttemptRejectsAnAttemptWithMissingRequiredAnswers() {
        ActivityAttempt attempt = ActivityAttempt.start(CHILD_ID, ACTIVITY_VERSION_ID, null, null, Instant.now());
        UUID answeredQuestion = UUID.randomUUID();
        UUID missingQuestion = UUID.randomUUID();
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(withId(attempt, ATTEMPT_ID)));
        when(activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(ACTIVITY_VERSION_ID))
                .thenReturn(requiredItems(answeredQuestion, missingQuestion));
        when(answerRecordRepository.findByActivityAttemptId(ATTEMPT_ID))
                .thenReturn(List.of(answer(answeredQuestion, true)));

        assertThatThrownBy(() -> service.completeAttempt(ATTEMPT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required questions have not been answered");
        assertThat(attempt.getStatus()).isEqualTo(ActivityAttemptStatus.IN_PROGRESS);
    }

    // ---- Known Backend Fix #2: attempt/learning-path integrity ----------------

    @Test
    void startAttemptRejectsALearningPathItemBelongingToADifferentChild() {
        lenient().when(childRepository.existsById(CHILD_ID)).thenReturn(true);
        givenLearningPathItem(OTHER_CHILD_ID, ACTIVITY_VERSION_ID);

        assertThatThrownBy(() -> service.startAttempt(CHILD_ID, ACTIVITY_VERSION_ID, LEARNING_PATH_ITEM_ID, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void startAttemptRejectsAnActivityVersionThatDoesNotMatchTheLearningPathItem() {
        lenient().when(childRepository.existsById(CHILD_ID)).thenReturn(true);
        // The path item is genuinely the child's own, but it points at a
        // different activity version than the one this attempt claims.
        givenLearningPathItem(CHILD_ID, OTHER_ACTIVITY_VERSION_ID);

        assertThatThrownBy(() -> service.startAttempt(CHILD_ID, ACTIVITY_VERSION_ID, LEARNING_PATH_ITEM_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startAttemptSucceedsWhenTheLearningPathItemGenuinelyBelongsToThisChildAndActivity() {
        when(childRepository.existsById(CHILD_ID)).thenReturn(true);
        givenLearningPathItem(CHILD_ID, ACTIVITY_VERSION_ID);
        when(attemptRepository.findByChildIdAndActivityVersionId(CHILD_ID, ACTIVITY_VERSION_ID))
                .thenReturn(List.of());
        when(attemptRepository.save(any(ActivityAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAttempt attempt = service.startAttempt(CHILD_ID, ACTIVITY_VERSION_ID, LEARNING_PATH_ITEM_ID, null);

        assertThat(attempt.getChildId()).isEqualTo(CHILD_ID);
        assertThat(attempt.getLearningPathItemId()).isEqualTo(LEARNING_PATH_ITEM_ID);
    }

    @Test
    void startAttemptSkipsPathValidationEntirelyForFreePracticeWithNoLearningPathItem() {
        when(childRepository.existsById(CHILD_ID)).thenReturn(true);
        when(attemptRepository.findByChildIdAndActivityVersionId(CHILD_ID, ACTIVITY_VERSION_ID))
                .thenReturn(List.of());
        when(attemptRepository.save(any(ActivityAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.startAttempt(CHILD_ID, ACTIVITY_VERSION_ID, null, null);

        verify(learningPathItemRepository, org.mockito.Mockito.never()).findById(any());
    }

    // ---------------------------------------------------------------- helpers ----

    private void givenLearningPathItem(UUID owningChildId, UUID itemActivityVersionId) {
        LearningPathItem item = LearningPathItem.place(
                LEARNING_PATH_ID, itemActivityVersionId, 0, LearningPathItemSource.CURRICULUM, Instant.now());
        LearningPath path = LearningPath.create(ENROLLMENT_ID);
        Enrollment enrollment = enrollmentOf(owningChildId);

        when(learningPathItemRepository.findById(LEARNING_PATH_ITEM_ID)).thenReturn(Optional.of(item));
        when(learningPathRepository.findById(LEARNING_PATH_ID)).thenReturn(Optional.of(path));
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    }

    private static List<ActivityItem> requiredItems(UUID... questionIds) {
        java.util.ArrayList<ActivityItem> items = new java.util.ArrayList<>();
        for (int index = 0; index < questionIds.length; index++) {
            items.add(ActivityItem.create(ACTIVITY_VERSION_ID, questionIds[index], index, BigDecimal.ONE));
        }
        return items;
    }

    private static AnswerRecord answer(UUID questionVersionId, boolean correct) {
        return AnswerRecord.record(ATTEMPT_ID, questionVersionId, "x", correct, 5);
    }

    private static ActivityVersion publishedActivityVersion(UUID activityVersionId) {
        ActivityVersion version = ActivityVersion.draft(UUID.randomUUID(), 1, "Instructions", 1, 60);
        version.publish(Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", activityVersionId);
        return version;
    }

    private static Enrollment enrollmentOf(UUID childId) {
        return Enrollment.enroll(childId, UUID.randomUUID(), Instant.now());
    }

    /** Mockito can't intercept the AuditableEntity id assigned by the DB, so tests that need a stable id for findById stubs set it via reflection. */
    private static ActivityAttempt withId(ActivityAttempt attempt, UUID id) {
        org.springframework.test.util.ReflectionTestUtils.setField(attempt, "id", id);
        return attempt;
    }

}
