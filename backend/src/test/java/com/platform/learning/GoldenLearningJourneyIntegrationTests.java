package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivitySkillRepository;
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
import com.platform.learner.entity.LearnerObjectiveState;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.service.AdaptiveEngine;
import com.platform.learner.service.MasteryEngine;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.EnrollmentService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the mission's golden journey end-to-end against REAL seeded content
 * (V29__seed_addition_within_10_foundation.sql), using the exact same production
 * services the HTTP controllers call - no re-implementation of the engine's rules
 * here, and no hardcoded ids: every piece of content is resolved by code/subject
 * lookup, so this test would keep working even if V29's literal UUIDs changed.
 *
 * <p>The central claim (mission Section 6): SAME CONTENT + DIFFERENT EVIDENCE =
 * DIFFERENT LEARNING PATH. Two children are enrolled in the identical curriculum;
 * one is fed mostly-correct answers, the other mostly-incorrect answers, both on the
 * "Add within 10" skill. Everything downstream (mastery, AdaptiveEngine's
 * direction/difficulty pick, whether Smart Review fires, which ParentInsight types
 * are generated) is asserted to differ, and every assertion reads it back from the
 * actual persisted state - never a value this test invents.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class GoldenLearningJourneyIntegrationTests {

    private static final String SUBJECT_CODE = "MATHS";
    private static final String AGE_HUB_CODE = "JUNIOR";
    private static final String SKILL_RECOGNITION_CODE = "MATHS_ADD_RECOGNITION";
    private static final String SKILL_WITHIN_5_CODE = "MATHS_ADD_WITHIN_5";
    private static final String SKILL_WITHIN_10_CODE = "MATHS_ADD_WITHIN_10";
    private static final String SKILL_FLUENCY_10_CODE = "MATHS_ADD_FLUENCY_10";

    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private CurriculumRepository curriculumRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private ActivitySkillRepository activitySkillRepository;
    @Autowired private ActivityVersionRepository activityVersionRepository;
    @Autowired private ActivityItemRepository activityItemRepository;
    @Autowired private QuestionVersionRepository questionVersionRepository;

    @Autowired private FamilyRepository familyRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private MasteryEngine masteryEngine;
    @Autowired private AdaptiveEngine adaptiveEngine;
    @Autowired private SmartReviewService smartReviewService;
    @Autowired private ParentInsightService parentInsightService;

    /**
     * The full golden path for ONE learner, run twice with different evidence
     * (mission Section 6) so a single test method is the actual proof of divergence
     * rather than two separately-plausible-looking tests that never compare notes.
     */
    @Test
    void sameContentDifferentEvidenceProducesDifferentLearningOutcomes() {
        UUID within5SkillId = requireSkill(SKILL_WITHIN_5_CODE).getId();
        UUID within10SkillId = requireSkill(SKILL_WITHIN_10_CODE).getId();
        UUID recognitionSkillId = requireSkill(SKILL_RECOGNITION_CODE).getId();
        UUID fluency10SkillId = requireSkill(SKILL_FLUENCY_10_CODE).getId();
        UUID curriculumId = requireCurriculum();

        List<ActivityVersion> within10Versions = publishedVersionsForSkill(within10SkillId);
        assertThat(within10Versions)
                .as("V29 must publish more than one difficulty tier for 'Add within 10' "
                        + "or AdaptiveEngine has nothing to differentiate between")
                .hasSizeGreaterThanOrEqualTo(3);
        ActivityVersion easiestWithin10 = within10Versions.get(0);
        ActivityVersion hardestWithin10 = within10Versions.get(within10Versions.size() - 1);

        // ---- Learner A: enrolled, then answers "Add within 10" mostly correctly ----
        UUID learnerAId = enrollChild("Learner A", curriculumId);
        // Broad coverage across the whole objective, not just one skill - see this
        // class's javadoc / the mission handoff notes for why: the objective-level
        // rollup ParentInsight reads is a plain average across every skill mapped to
        // it, so a learner who has only ever touched one of four skills reads as
        // barely-started at the objective level even with perfect evidence on that
        // one skill. Real practice sessions touch the whole lesson; this mirrors that.
        answerSkillRepeatedly(learnerAId, recognitionSkillId, true, 6);
        answerSkillRepeatedly(learnerAId, within5SkillId, true, 6);
        answerSkillRepeatedly(learnerAId, fluency10SkillId, true, 6);
        answerSkillRepeatedly(learnerAId, within10SkillId, true, 8);

        // ---- Learner B: same curriculum, mostly incorrect on the same skill ----
        UUID learnerBId = enrollChild("Learner B", curriculumId);
        answerSkillOutcomes(learnerBId, within10SkillId, List.of(true, false, false, false, false));

        // ---- Evidence: real AnswerRecords produced real, divergent LearnerSkillState ----
        LearnerSkillState stateA = requireSkillState(learnerAId, within10SkillId);
        LearnerSkillState stateB = requireSkillState(learnerBId, within10SkillId);

        assertThat(stateA.getEvidenceCount()).isEqualTo(8);
        assertThat(stateB.getEvidenceCount()).isEqualTo(5);
        assertThat(stateA.getMasteryProbability())
                .as("mostly-correct learner must end up with materially higher mastery "
                        + "than the mostly-incorrect learner on the identical skill")
                .isGreaterThan(stateB.getMasteryProbability());
        assertThat(stateA.getMasteryProbability()).isGreaterThanOrEqualTo(BigDecimal.valueOf(0.8));
        assertThat(stateB.getMasteryProbability()).isLessThan(BigDecimal.valueOf(0.4));

        // ---- Adaptive Engine: same skill, same content pool, opposite decisions ----
        LearningPath pathA = enrollmentService.getLearningPath(learnerEnrollmentId(learnerAId));
        LearningPath pathB = enrollmentService.getLearningPath(learnerEnrollmentId(learnerBId));

        Optional<AdaptiveEngine.Recommendation> recA =
                adaptiveEngine.recommendNext(learnerAId, within10SkillId, pathA.getId());
        Optional<AdaptiveEngine.Recommendation> recB =
                adaptiveEngine.recommendNext(learnerBId, within10SkillId, pathB.getId());

        assertThat(recA).isPresent();
        assertThat(recB).isPresent();
        assertThat(recA.get().direction()).isEqualTo(AdaptiveEngine.Direction.CHALLENGE);
        assertThat(recB.get().direction()).isEqualTo(AdaptiveEngine.Direction.REINFORCEMENT);

        // Challenge -> hardest published version of the SAME skill; reinforcement ->
        // the prerequisite skill (within5, per V29's skill_prerequisites chain), not
        // within10 at all. This is the concrete "different next activity" proof.
        assertThat(recA.get().insertedItem().getActivityVersionId()).isEqualTo(hardestWithin10.getId());
        assertThat(recB.get().targetSkillId()).isEqualTo(within5SkillId);
        assertThat(recA.get().insertedItem().getActivityVersionId())
                .isNotEqualTo(recB.get().insertedItem().getActivityVersionId());

        // ---- Smart Review: only the struggling learner is flagged, and for the ----
        // ---- actual weak skill, not a generic placeholder review item. ----
        Optional<ReviewItem> reviewA = smartReviewService.evaluateAndSchedule(learnerAId, within10SkillId);
        Optional<ReviewItem> reviewB = smartReviewService.evaluateAndSchedule(learnerBId, within10SkillId);

        assertThat(reviewA).as("a mastered skill must not be flagged for review").isEmpty();
        assertThat(reviewB).isPresent();
        assertThat(reviewB.get().getSkillId()).isEqualTo(within10SkillId);
        assertThat(smartReviewService.listActive(learnerAId)).isEmpty();
        assertThat(smartReviewService.listActive(learnerBId))
                .extracting(ReviewItem::getSkillId)
                .containsExactly(within10SkillId);

        // ---- Parent Insight: generated from the real state above, nothing hardcoded ----
        List<ParentInsight> insightsA = parentInsightService.generateInsights(learnerAId);
        List<ParentInsight> insightsB = parentInsightService.generateInsights(learnerBId);

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

        // ---- Sanity: the two learners' objective-level rollups differ too ----
        List<LearnerObjectiveState> objectiveStatesA = masteryEngine.listObjectiveStates(learnerAId);
        List<LearnerObjectiveState> objectiveStatesB = masteryEngine.listObjectiveStates(learnerBId);
        assertThat(objectiveStatesA).hasSize(1);
        assertThat(objectiveStatesB).hasSize(1);
        assertThat(objectiveStatesA.get(0).getMasteryProbability())
                .isGreaterThan(objectiveStatesB.get(0).getMasteryProbability());
    }

    // ---------------------------------------------------------------- helpers ----

    private UUID enrollChild(String displayName, UUID curriculumId) {
        Family family = familyRepository.save(Family.create(displayName + "'s Family"));
        Child child = childRepository.save(Child.enroll(family.getId(), displayName, LocalDate.of(2018, 6, 1)));
        Enrollment enrollment = enrollmentService.enroll(child.getId(), curriculumId);
        enrollmentIds.put(child.getId(), enrollment.getId());
        return child.getId();
    }

    private final java.util.Map<UUID, UUID> enrollmentIds = new java.util.HashMap<>();

    private UUID learnerEnrollmentId(UUID childId) {
        return enrollmentIds.get(childId);
    }

    /** Submits {@code count} answers for one skill, all with the same correctness. */
    private void answerSkillRepeatedly(UUID childId, UUID skillId, boolean correct, int count) {
        answerSkillOutcomes(childId, skillId, java.util.Collections.nCopies(count, correct));
    }

    /**
     * Submits one answer per entry in {@code outcomes} (in order) for the given
     * skill, cycling through whichever published activity versions address that
     * skill, and completing each attempt - this is the real
     * ActivityAttemptService.startAttempt -&gt; submitAnswer -&gt; completeAttempt path,
     * the same one ActivityAttemptController drives, just called directly.
     */
    private void answerSkillOutcomes(UUID childId, UUID skillId, List<Boolean> outcomes) {
        List<ActivityVersion> versions = publishedVersionsForSkill(skillId);
        assertThat(versions).as("no published activity found for skill " + skillId).isNotEmpty();

        int i = 0;
        for (boolean wantCorrect : outcomes) {
            ActivityVersion version = versions.get(i % versions.size());
            i++;

            ActivityAttempt attempt = activityAttemptService.startAttempt(childId, version.getId(), null, null);

            var items = activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(version.getId());
            assertThat(items).as("seeded activity version has no questions").isNotEmpty();
            UUID questionVersionId = items.get(0).getQuestionVersionId();

            QuestionVersion questionVersion = questionVersionRepository.findById(questionVersionId).orElseThrow();
            String submitted = wantCorrect
                    ? questionVersion.getCorrectAnswer()
                    : wrongNumericAnswer(questionVersion.getCorrectAnswer());

            activityAttemptService.submitAnswer(
                    attempt.getId(), questionVersionId, submitted, 10, UUID.randomUUID().toString());
            activityAttemptService.completeAttempt(attempt.getId());
        }
    }

    /** Any numeric string guaranteed not to equal the correct answer. */
    private static String wrongNumericAnswer(String correctAnswer) {
        return String.valueOf(new BigDecimal(correctAnswer).intValue() + 1000);
    }

    private List<ActivityVersion> publishedVersionsForSkill(UUID skillId) {
        return activitySkillRepository.findBySkillId(skillId).stream()
                .map(ActivitySkill::getActivityId)
                .distinct()
                .map(activityId -> activityVersionRepository
                        .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(activityId, PublicationStatus.PUBLISHED))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(ActivityVersion::getDifficultyLevel, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
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

}
