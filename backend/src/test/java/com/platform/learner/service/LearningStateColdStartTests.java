package com.platform.learner.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.knowledge.repository.SkillPrerequisiteRepository;
import com.platform.learner.MasteryProperties;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerObjectiveStateRepository;
import com.platform.learner.repository.LearnerSkillHistoryRepository;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.service.LearningPathService;
import com.platform.review.entity.ReviewItem;
import com.platform.review.entity.ReviewSchedule;
import com.platform.review.repository.ReviewAttemptRepository;
import com.platform.review.repository.ReviewItemRepository;
import com.platform.review.repository.ReviewScheduleRepository;
import com.platform.review.service.SmartReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.platform.learning.service.ContentScopes;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Battle 5B: the cold-start chain end to end, with the REAL MasteryEngine producing
 * the learner state and the REAL AdaptiveEngine / SmartReviewService deciding from
 * it (only persistence is mocked). The invariant under test: "we have little
 * evidence" must never be read as "this child is struggling", and a child's early
 * correct answers must never trigger remediation or review.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningStateColdStartTests {

    private static final UUID CHILD = UUID.randomUUID();
    private static final UUID SKILL = UUID.randomUUID();
    private static final UUID PATH = UUID.randomUUID();

    @Mock private LearnerSkillStateRepository skillStateRepository;
    @Mock private LearnerSkillHistoryRepository skillHistoryRepository;
    @Mock private LearnerObjectiveStateRepository objectiveStateRepository;
    @Mock private LearningObjectiveSkillRepository learningObjectiveSkillRepository;
    @Mock private SkillPrerequisiteRepository skillPrerequisiteRepository;
    @Mock private ActivityVersionSkillRepository activitySkillRepository;
    @Mock private ActivityVersionRepository activityVersionRepository;
    @Mock private LearningPathService learningPathService;
    @Mock private ReviewItemRepository reviewItemRepository;
    @Mock private ReviewScheduleRepository reviewScheduleRepository;
    @Mock private ReviewAttemptRepository reviewAttemptRepository;

    private final AtomicReference<LearnerSkillState> state = new AtomicReference<>();
    private MasteryEngine masteryEngine;
    private AdaptiveEngine adaptiveEngine;
    private SmartReviewService smartReviewService;

    @BeforeEach
    void setUp() {
        when(skillStateRepository.findByChildIdAndSkillId(CHILD, SKILL)).thenAnswer(i -> Optional.ofNullable(state.get()));
        when(skillStateRepository.save(any(LearnerSkillState.class))).thenAnswer(i -> {
            state.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(learningObjectiveSkillRepository.findBySkillId(SKILL)).thenReturn(List.of());

        masteryEngine = new MasteryEngine(skillStateRepository, skillHistoryRepository, objectiveStateRepository,
                learningObjectiveSkillRepository, new MasteryProperties(new BigDecimal("0.2"), 10, new BigDecimal("0.8")));
        adaptiveEngine = new AdaptiveEngine(skillStateRepository, skillPrerequisiteRepository, activitySkillRepository,
                activityVersionRepository, learningPathService, ContentScopes.admittingEverything());
        smartReviewService = new SmartReviewService(reviewItemRepository, reviewScheduleRepository, reviewAttemptRepository,
                skillStateRepository, activitySkillRepository, activityVersionRepository, learningPathService,
                ContentScopes.admittingEverything());

        // One published, not-yet-placed activity for the skill, so any REINFORCEMENT or
        // CHALLENGE decision has something to place and becomes observable.
        UUID activityId = UUID.randomUUID();
        ActivityVersion practice = ActivityVersion.draft(activityId, 1, "Practice", 1, 60);
        practice.publish(Instant.now());
        ReflectionTestUtils.setField(practice, "id", UUID.randomUUID());
        when(skillPrerequisiteRepository.findBySkillId(SKILL)).thenReturn(List.of());
        when(activitySkillRepository.findPublishedPractising(SKILL)).thenReturn(List.of(practice));
        when(learningPathService.listItems(PATH)).thenReturn(List.of());
        when(learningPathService.appendItem(eq(PATH), any(UUID.class), any())).thenAnswer(i ->
                LearningPathItem.place(PATH, i.getArgument(1), 0, i.getArgument(2), Instant.now()));

        // Battle 5D (N2): review items are created with insert-if-absent and then read back
        // under a row lock; this stands in for that table.
        java.util.concurrent.atomic.AtomicReference<ReviewItem> storedReviewItem = new java.util.concurrent.atomic.AtomicReference<>();
        when(reviewItemRepository.insertIfAbsent(any(UUID.class), eq(CHILD), eq(SKILL))).thenAnswer(i -> {
            if (storedReviewItem.get() != null) {
                return 0;
            }
            ReviewItem item = ReviewItem.create(CHILD, SKILL);
            ReflectionTestUtils.setField(item, "id", i.getArgument(0));
            storedReviewItem.set(item);
            return 1;
        });
        when(reviewItemRepository.findByChildIdAndSkillIdForUpdate(CHILD, SKILL))
                .thenAnswer(i -> Optional.ofNullable(storedReviewItem.get()));
        when(reviewScheduleRepository.findByReviewItemId(any(UUID.class))).thenReturn(Optional.empty());
        when(reviewScheduleRepository.save(any(ReviewSchedule.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void zeroEvidenceProducesNoDecisionAtAll() {
        assertThat(adaptiveDirection()).isEmpty();
        assertThat(flaggedForReview()).isFalse();
        verify(learningPathService, never()).lockPath(PATH);
    }

    @Test
    void firstCorrectAnswerIsFullAccuracyButNotYetEnoughForChallenge() {
        answers(true);

        assertThat(mastery()).isEqualByComparingTo("1.0000");
        assertThat(adaptiveDirection()).isEmpty(); // CONTINUE: 1 answer is not evidence of mastery
        assertThat(flaggedForReview()).isFalse();
    }

    @Test
    void firstIncorrectAnswerIsWeakEvidenceNotAStrugglingChild() {
        answers(false);

        assertThat(mastery()).isEqualByComparingTo("0.0000");
        assertThat(adaptiveDirection()).isEmpty(); // no remediation from a single answer
        assertThat(flaggedForReview()).isFalse();
    }

    @Test
    void twoOfTwoCorrectIsNeverRemediationOrReview() {
        answers(true, true);

        // Before Battle 5B this read 0.36 - below both 0.4 (REINFORCEMENT) and 0.5 (review).
        assertThat(mastery()).isEqualByComparingTo("1.0000");
        assertThat(adaptiveDirection()).isEmpty();
        assertThat(flaggedForReview()).isFalse();
    }

    @Test
    void twoOfTwoIncorrectIsConsistentEvidenceForReinforcementAndReview() {
        answers(false, false);

        assertThat(mastery()).isEqualByComparingTo("0.0000");
        assertThat(adaptiveDirection()).contains(AdaptiveEngine.Direction.REINFORCEMENT);
        assertThat(flaggedForReview()).isTrue();
    }

    @Test
    void mixedEvidenceIsTheRecencyWeightedAccuracyOfWhatWasObserved() {
        answers(true, false);

        assertThat(mastery()).isEqualByComparingTo("0.4444"); // latest (wrong) answer weighs more
        assertThat(adaptiveDirection()).isEmpty();            // 0.44 >= 0.4: continue, not remediate
        assertThat(flaggedForReview()).isTrue();              // 0.44 < 0.5 with 2 answers: review
    }

    @Test
    void mixedEvidenceEndingCorrectIsNotFlagged() {
        answers(false, true);

        assertThat(mastery()).isEqualByComparingTo("0.5556");
        assertThat(adaptiveDirection()).isEmpty();
        assertThat(flaggedForReview()).isFalse();
    }

    @Test
    void threeOfThreeCorrectReachesChallengeUnderTheExistingEvidenceGate() {
        answers(true, true, true);

        assertThat(mastery()).isEqualByComparingTo("1.0000");
        assertThat(adaptiveDirection()).contains(AdaptiveEngine.Direction.CHALLENGE);
        assertThat(flaggedForReview()).isFalse();
    }

    @Test
    void mostlyCorrectWithARecentMissIsNotRemediation() {
        answers(true, true, true, false); // 75% correct; the prior-0 value was 0.39 (REINFORCEMENT)

        assertThat(mastery()).isCloseTo(new BigDecimal("0.6612"), within(new BigDecimal("0.0001")));
        assertThat(adaptiveDirection()).isEmpty();
        assertThat(flaggedForReview()).isFalse();
    }

    @Test
    void mostlyIncorrectIsStillRemediatedAndReviewed() {
        answers(false, false, false, true);

        assertThat(mastery()).isCloseTo(new BigDecimal("0.3388"), within(new BigDecimal("0.0001")));
        assertThat(adaptiveDirection()).contains(AdaptiveEngine.Direction.REINFORCEMENT);
        assertThat(flaggedForReview()).isTrue();
    }

    @Test
    void longRunBehavesLikeThePlainEwmaSoRecencyIsPreserved() {
        boolean[] outcomes = new boolean[50];
        java.util.Arrays.fill(outcomes, 0, 49, true); // 49 correct, then one miss
        answers(outcomes);

        // step size has converged to the learning rate: 1.0 - 0.2 * (1.0 - 0) = 0.8
        assertThat(mastery()).isCloseTo(new BigDecimal("0.8000"), within(new BigDecimal("0.0005")));
    }

    @Test
    void evidenceStrengthIsStillTrackedSeparatelyFromTheEstimate() {
        answers(true, true);

        assertThat(state.get().getEvidenceCount()).isEqualTo(2);
        assertThat(state.get().getCorrectCount()).isEqualTo(2);
        assertThat(state.get().getConfidence()).isEqualByComparingTo("0.2000"); // 2 of saturation 10
    }

    private void answers(boolean... outcomes) {
        for (boolean correct : outcomes) {
            masteryEngine.recordEvidence(CHILD, SKILL, correct, UUID.randomUUID());
        }
    }

    private BigDecimal mastery() {
        return state.get().getMasteryProbability();
    }

    private Optional<AdaptiveEngine.Direction> adaptiveDirection() {
        return adaptiveEngine.recommendNext(CHILD, SKILL, PATH).map(AdaptiveEngine.Recommendation::direction);
    }

    private boolean flaggedForReview() {
        return smartReviewService.evaluateAndSchedule(CHILD, SKILL).isPresent();
    }

}
