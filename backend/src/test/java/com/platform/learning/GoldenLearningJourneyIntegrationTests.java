package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.entity.CatalogStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersionSkill;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.family.entity.Family;
import com.platform.family.repository.FamilyRepository;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.learner.entity.AdaptationDecision;
import com.platform.learner.entity.AdaptationDecisionType;
import com.platform.learner.entity.LearnerObjectiveState;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.AdaptationDecisionRepository;
import com.platform.learner.service.MasteryEngine;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.EnrollmentService;
import com.platform.learning.service.LearningPathService;
import com.platform.parent.entity.ParentInsight;
import com.platform.parent.entity.ParentInsightType;
import com.platform.parent.service.ParentInsightService;
import com.platform.review.entity.ReviewItem;
import com.platform.review.service.SmartReviewService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The golden learning journey, end to end against REAL seeded content
 * (V29__seed_addition_within_10_foundation.sql) and the same production services
 * the HTTP controllers call. No ids are hardcoded: content is resolved by code.
 *
 * <p>The central claim: SAME CONTENT + DIFFERENT EVIDENCE = DIFFERENT LEARNING PATH.
 * Two children enrol in the same curriculum and each works through their own
 * learning path exactly as the app does: next activity -&gt; start -&gt; answer EVERY
 * required question -&gt; complete. One answers everything correctly; the other gets
 * every "Add within 10" question wrong.
 *
 * <p>Battle 5D: the journey never calls the Adaptive Engine or Smart Review itself.
 * Completing an attempt is what makes those decisions (server-authoritative
 * post-completion), so every assertion reads persisted state the completions
 * produced: evidence, mastery, one recorded adaptive decision per completion, review
 * state, path placements, and the Parent Insight derived from all of it.
 *
 * <p>Rehabilitated in 5D: the previous version answered only the first question of
 * each attempt, which accepted completion rules (Known Backend Fix A / Battle 5A)
 * correctly refuse. It also drove adaptive and review decisions by hand, which is
 * exactly what 5D moves to the server.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class GoldenLearningJourneyIntegrationTests {

    private static final String SUBJECT_CODE = "MATHS";
    private static final String AGE_HUB_CODE = "JUNIOR";
    private static final String SKILL_WITHIN_5_CODE = "MATHS_ADD_WITHIN_5";
    private static final String SKILL_WITHIN_10_CODE = "MATHS_ADD_WITHIN_10";
    /** Upper bound on activities one learner plays; the paths here finish well inside it. */
    private static final int MAX_STEPS = 30;

    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private CurriculumRepository curriculumRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private ActivityVersionSkillRepository activitySkillRepository;
    @Autowired private ActivityVersionRepository activityVersionRepository;
    @Autowired private ActivityItemRepository activityItemRepository;
    @Autowired private QuestionVersionRepository questionVersionRepository;

    @Autowired private FamilyRepository familyRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private LearningPathService learningPathService;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private MasteryEngine masteryEngine;
    @Autowired private AdaptationDecisionRepository adaptationDecisionRepository;
    @Autowired private SmartReviewService smartReviewService;
    @Autowired private ParentInsightService parentInsightService;

    @Test
    void sameContentDifferentEvidenceProducesDifferentLearningOutcomes() {
        UUID within5SkillId = requireSkill(SKILL_WITHIN_5_CODE).getId();
        UUID within10SkillId = requireSkill(SKILL_WITHIN_10_CODE).getId();
        UUID curriculumId = requireCurriculum();

        Learner a = enroll("Learner A", curriculumId);
        Learner b = enroll("Learner B", curriculumId);

        // ---- Each child works through their own path exactly as the app does ----
        List<ActivityAttempt> attemptsA = playPath(a, activityVersionId -> true);
        List<ActivityAttempt> attemptsB = playPath(b, activityVersionId -> !exercises(activityVersionId, within10SkillId));

        // ---- Every completion answered every required question, and scored it ----
        for (ActivityAttempt attempt : concat(attemptsA, attemptsB)) {
            ActivityAttempt stored = activityAttemptService.getAttempt(attempt.getId());
            assertThat(stored.getStatus()).isEqualTo(ActivityAttemptStatus.COMPLETED);
            assertThat(activityAttemptService.listAnswers(attempt.getId()))
                    .hasSameSizeAs(activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(attempt.getActivityVersionId()));
            assertThat(stored.getScore()).isNotNull();
        }

        // ---- Evidence and mastery: same skill, divergent learner state ----
        LearnerSkillState stateA = requireSkillState(a.childId(), within10SkillId);
        LearnerSkillState stateB = requireSkillState(b.childId(), within10SkillId);
        assertThat(stateA.getEvidenceCount()).isEqualTo(answersOnSkill(attemptsA, within10SkillId));
        assertThat(stateB.getEvidenceCount()).isEqualTo(answersOnSkill(attemptsB, within10SkillId));
        assertThat(stateA.getMasteryProbability()).isGreaterThanOrEqualTo(BigDecimal.valueOf(0.8));
        assertThat(stateB.getMasteryProbability()).isLessThan(BigDecimal.valueOf(0.4));

        // ---- Server-authoritative decisions: exactly one per path completion, made by completing ----
        List<AdaptationDecision> decisionsA = adaptationDecisionRepository.findByChildIdOrderByOccurredAtAsc(a.childId());
        List<AdaptationDecision> decisionsB = adaptationDecisionRepository.findByChildIdOrderByOccurredAtAsc(b.childId());
        assertThat(decisionsA).extracting(AdaptationDecision::getActivityAttemptId)
                .containsExactlyInAnyOrderElementsOf(ids(attemptsA));
        assertThat(decisionsB).extracting(AdaptationDecision::getActivityAttemptId)
                .containsExactlyInAnyOrderElementsOf(ids(attemptsB));

        // Opposite decisions on the same skill: A is challenged on "within 10", B is
        // reinforced on its prerequisite "within 5" (V29's skill_prerequisites chain).
        assertThat(decisionsA).anyMatch(d -> d.getDecisionType() == AdaptationDecisionType.INCREASE_DIFFICULTY
                && within10SkillId.toString().equals(d.getContext().get("governingSkillId")));
        assertThat(decisionsA).noneMatch(d -> d.getDecisionType() == AdaptationDecisionType.REMEDIATE);
        assertThat(decisionsB).anyMatch(d -> d.getDecisionType() == AdaptationDecisionType.REMEDIATE
                && within10SkillId.toString().equals(d.getContext().get("governingSkillId"))
                && within5SkillId.toString().equals(d.getContext().get("targetSkillId")));
        assertThat(decisionsB).noneMatch(d -> d.getDecisionType() == AdaptationDecisionType.INCREASE_DIFFICULTY
                && within10SkillId.toString().equals(d.getContext().get("governingSkillId")));

        // ---- Smart Review: only the struggling learner, for the actual weak skill, ----
        // ---- placed on their path by the server and played through next-activity ----
        assertThat(smartReviewService.listActive(a.childId())).isEmpty();
        assertThat(smartReviewService.listActive(b.childId())).extracting(ReviewItem::getSkillId)
                .containsExactly(within10SkillId);
        assertThat(learningPathService.listItems(a.pathId()))
                .noneMatch(item -> item.getSource() == LearningPathItemSource.REVIEW);
        assertThat(learningPathService.listItems(b.pathId()))
                .anyMatch(item -> item.getSource() == LearningPathItemSource.REVIEW
                        && exercises(item.getActivityVersionId(), within10SkillId));
        assertThat(smartReviewService.findOutcome(reviewAttemptOf(b, attemptsB).getId())).isPresent();

        // ---- Parent Insight: generated from the persisted state above ----
        List<ParentInsight> insightsA = parentInsightService.generateInsights(a.childId());
        List<ParentInsight> insightsB = parentInsightService.generateInsights(b.childId());

        assertThat(insightsA)
                .as("the strong learner must not receive a weakness/recommendation insight")
                .noneMatch(i -> i.getInsightType() == ParentInsightType.WEAKNESS)
                .noneMatch(i -> i.getInsightType() == ParentInsightType.RECOMMENDATION);
        assertThat(insightsA).anyMatch(i -> i.getInsightType() == ParentInsightType.STRENGTH
                || i.getInsightType() == ParentInsightType.PROGRESS);

        assertThat(insightsB)
                .as("the struggling learner must receive a recommendation traceable to the actual weak skill")
                .anyMatch(i -> i.getInsightType() == ParentInsightType.RECOMMENDATION
                        && String.valueOf(within10SkillId).equals(i.getSupportingData().get("skillId")));
        assertThat(insightsB).anyMatch(i -> i.getInsightType() == ParentInsightType.WEAKNESS);
        assertThat(insightsB)
                .as("the struggling learner's insights must not falsely claim mastery")
                .noneMatch(i -> i.getInsightType() == ParentInsightType.STRENGTH);

        // ---- The objective-level rollups differ too ----
        List<LearnerObjectiveState> objectiveStatesA = masteryEngine.listObjectiveStates(a.childId());
        List<LearnerObjectiveState> objectiveStatesB = masteryEngine.listObjectiveStates(b.childId());
        assertThat(objectiveStatesA).hasSize(1);
        assertThat(objectiveStatesB).hasSize(1);
        assertThat(objectiveStatesA.get(0).getMasteryProbability())
                .isGreaterThan(objectiveStatesB.get(0).getMasteryProbability());
    }

    // ---------------------------------------------------------------- the loop ----

    /**
     * Plays the child's path through the same service calls the app makes, until the
     * path has nothing left: next item -&gt; start it -&gt; answer every required question
     * (correctly when {@code answersCorrectly} says so) -&gt; complete. Nothing else is
     * called - adaptive and review placements appear on the path only because
     * completing made them.
     */
    private List<ActivityAttempt> playPath(Learner learner, Predicate<UUID> answersCorrectly) {
        List<ActivityAttempt> completed = new ArrayList<>();
        for (int step = 0; step < MAX_STEPS; step++) {
            LearningPathItem next;
            try {
                next = learningPathService.getNextItem(learner.pathId());
            } catch (ResourceNotFoundException nothingLeft) {
                return completed;
            }
            ActivityAttempt attempt = activityAttemptService.startAttempt(
                    learner.childId(), next.getActivityVersionId(), next.getId(), null);
            boolean correct = answersCorrectly.test(next.getActivityVersionId());
            for (ActivityItem item : activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(next.getActivityVersionId())) {
                String answer = questionVersionRepository.findById(item.getQuestionVersionId()).orElseThrow().getCorrectAnswer();
                activityAttemptService.submitAnswer(attempt.getId(), item.getQuestionVersionId(),
                        correct ? answer : wrongNumericAnswer(answer), 10, UUID.randomUUID().toString());
            }
            completed.add(activityAttemptService.completeAttempt(attempt.getId()));
        }
        throw new AssertionError("path did not finish within " + MAX_STEPS + " activities");
    }

    // ---------------------------------------------------------------- helpers ----

    private boolean exercises(UUID activityVersionId, UUID skillId) {
        // PD-08: what the version played practises, by its own tags.
        return activitySkillRepository.findByActivityVersionIdOrderByCreatedAtAscIdAsc(activityVersionId).stream()
                .map(ActivityVersionSkill::getSkillId).anyMatch(skillId::equals);
    }

    /** Every seeded question carries its activity's skill, so this is the evidence count the skill must show. */
    private long answersOnSkill(List<ActivityAttempt> attempts, UUID skillId) {
        return attempts.stream()
                .filter(attempt -> exercises(attempt.getActivityVersionId(), skillId))
                .mapToLong(attempt -> activityAttemptService.listAnswers(attempt.getId()).size())
                .sum();
    }

    private ActivityAttempt reviewAttemptOf(Learner learner, List<ActivityAttempt> attempts) {
        Set<UUID> reviewItemIds = learningPathService.listItems(learner.pathId()).stream()
                .filter(item -> item.getSource() == LearningPathItemSource.REVIEW)
                .map(LearningPathItem::getId)
                .collect(Collectors.toSet());
        return attempts.stream()
                .filter(attempt -> reviewItemIds.contains(attempt.getLearningPathItemId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the placed review was never played through next-activity"));
    }

    private static List<ActivityAttempt> concat(List<ActivityAttempt> first, List<ActivityAttempt> second) {
        List<ActivityAttempt> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static List<UUID> ids(List<ActivityAttempt> attempts) {
        return attempts.stream().map(ActivityAttempt::getId).toList();
    }

    /** Any numeric string guaranteed not to equal the correct answer. */
    private static String wrongNumericAnswer(String correctAnswer) {
        return String.valueOf(new BigDecimal(correctAnswer).intValue() + 1000);
    }

    private Learner enroll(String displayName, UUID curriculumId) {
        Family family = familyRepository.save(Family.create(displayName + "'s Family"));
        Child child = childRepository.save(Child.enroll(family.getId(), displayName, LocalDate.of(2018, 6, 1)));
        Enrollment enrollment = enrollmentService.enroll(child.getId(), curriculumId);
        return new Learner(child.getId(), enrollmentService.getLearningPath(enrollment.getId()).getId());
    }

    private Skill requireSkill(String code) {
        return skillRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Seed migration V29 did not create skill " + code));
    }

    private UUID requireCurriculum() {
        var subject = subjectRepository.findByCode(SUBJECT_CODE)
                .orElseThrow(() -> new AssertionError("Seed migration V29 did not create subject " + SUBJECT_CODE));
        var ageHub = ageHubRepository.findByCode(AGE_HUB_CODE)
                .orElseThrow(() -> new AssertionError("Seed migration V29 did not create age hub " + AGE_HUB_CODE));

        List<Curriculum> curricula = curriculumRepository
                .findBySubjectIdAndAgeHubIdAndStatus(subject.getId(), ageHub.getId(), CatalogStatus.ACTIVE);
        assertThat(curricula).as("Seed migration V29 did not create the Maths/Junior curriculum").hasSize(1);
        return curricula.get(0).getId();
    }

    private LearnerSkillState requireSkillState(UUID childId, UUID skillId) {
        return masteryEngine.listSkillStates(childId).stream()
                .filter(s -> s.getSkillId().equals(skillId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a LearnerSkillState for skill " + skillId));
    }

    private record Learner(UUID childId, UUID pathId) {
    }

}
