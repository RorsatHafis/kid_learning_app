package com.platform.learner;

import com.platform.TestcontainersConfiguration;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivitySkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.family.entity.Family;
import com.platform.family.repository.FamilyRepository;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learner.service.AdaptiveEngine;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.repository.LearningPathRepository;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.EnrollmentService;
import com.platform.parent.entity.ParentInsight;
import com.platform.parent.entity.ParentInsightType;
import com.platform.parent.service.ParentInsightService;
import com.platform.review.service.SmartReviewService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Battle 5B against real PostgreSQL (Flyway through V34, real services): a child's
 * early evidence produces a truthful learner state all the way to Parent Insight.
 * A perfect start is not remediated, reviewed, or reported as needing practice; a
 * consistently wrong start still is.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class ColdStartLearningStateIntegrationTests {

    private static final String RECOGNITION_SKILL = "MATHS_ADD_RECOGNITION";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private CurriculumRepository curriculumRepository;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private LearningPathRepository learningPathRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private ActivitySkillRepository activitySkillRepository;
    @Autowired private ActivityVersionRepository activityVersionRepository;
    @Autowired private ActivityItemRepository activityItemRepository;
    @Autowired private QuestionVersionRepository questionVersionRepository;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private LearnerSkillStateRepository learnerSkillStateRepository;
    @Autowired private AdaptiveEngine adaptiveEngine;
    @Autowired private SmartReviewService smartReviewService;
    @Autowired private ParentInsightService parentInsightService;

    @Test
    void v34RanWithItsConfigurationPlaceholdersResolved() {
        Boolean success = jdbc.queryForObject(
                "select success from flyway_schema_history where version = '34'", Boolean.class);
        assertThat(success).isTrue();
    }

    @Test
    void aPerfectStartIsNotRemediatedReviewedOrReportedAsNeedingPractice() {
        Learner learner = enroll("Perfect Start");
        UUID skillId = recognitionSkillId();

        answerRecognitionActivity(learner.childId(), true);

        LearnerSkillState state = learnerSkillStateRepository.findByChildIdAndSkillId(learner.childId(), skillId).orElseThrow();
        assertThat(state.getEvidenceCount()).isEqualTo(2);
        assertThat(state.getMasteryProbability()).isEqualByComparingTo("1.0000"); // was 0.3600 before Battle 5B

        assertThat(adaptiveEngine.recommendNext(learner.childId(), skillId, learner.pathId())).isEmpty();
        assertThat(smartReviewService.evaluateAndSchedule(learner.childId(), skillId)).isEmpty();

        List<ParentInsight> insights = parentInsightService.generateInsights(learner.childId());
        assertThat(insights).noneMatch(insight -> insight.getInsightType() == ParentInsightType.RECOMMENDATION);
    }

    @Test
    void aConsistentlyWrongStartIsStillRemediatedReviewedAndExplained() {
        Learner learner = enroll("Struggling Start");
        UUID skillId = recognitionSkillId();

        answerRecognitionActivity(learner.childId(), false);

        LearnerSkillState state = learnerSkillStateRepository.findByChildIdAndSkillId(learner.childId(), skillId).orElseThrow();
        assertThat(state.getEvidenceCount()).isEqualTo(2);
        assertThat(state.getMasteryProbability()).isEqualByComparingTo("0.0000");

        assertThat(adaptiveEngine.recommendNext(learner.childId(), skillId, learner.pathId()))
                .hasValueSatisfying(rec -> assertThat(rec.direction()).isEqualTo(AdaptiveEngine.Direction.REINFORCEMENT));
        assertThat(smartReviewService.evaluateAndSchedule(learner.childId(), skillId)).isPresent();

        List<ParentInsight> insights = parentInsightService.generateInsights(learner.childId());
        assertThat(insights).anyMatch(insight -> insight.getInsightType() == ParentInsightType.RECOMMENDATION);
    }

    /** Both questions of the seeded Recognition activity, all correct or all wrong, then completion. */
    private void answerRecognitionActivity(UUID childId, boolean correct) {
        ActivityVersion version = activitySkillRepository.findBySkillId(recognitionSkillId()).stream()
                .map(ActivitySkill::getActivityId)
                .map(id -> activityVersionRepository
                        .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(id, PublicationStatus.PUBLISHED))
                .flatMap(java.util.Optional::stream)
                .findFirst().orElseThrow();

        // Free practice (no learning-path item), so the child's curriculum placement for
        // this activity stays PENDING and AdaptiveEngine's decision remains observable.
        ActivityAttempt attempt = activityAttemptService.startAttempt(childId, version.getId(), null, null);
        List<ActivityItem> items = activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(version.getId());
        assertThat(items).hasSize(2);
        for (ActivityItem item : items) {
            String correctAnswer = questionVersionRepository.findById(item.getQuestionVersionId()).orElseThrow().getCorrectAnswer();
            String submitted = correct ? correctAnswer : new BigDecimal(correctAnswer).add(BigDecimal.valueOf(1000)).toPlainString();
            activityAttemptService.submitAnswer(attempt.getId(), item.getQuestionVersionId(), submitted, 5, UUID.randomUUID().toString());
        }
        activityAttemptService.completeAttempt(attempt.getId());
    }

    private Learner enroll(String name) {
        Family family = familyRepository.save(Family.create(name + "'s Family"));
        Child child = childRepository.save(Child.enroll(family.getId(), name, LocalDate.of(2018, 6, 1)));
        Curriculum maths = curriculumRepository.findByStatus(CatalogStatus.ACTIVE).stream()
                .filter(c -> c.getName().equals("Maths - Junior")).findFirst().orElseThrow();
        Enrollment enrollment = enrollmentService.enroll(child.getId(), maths.getId());
        UUID pathId = learningPathRepository.findByEnrollmentId(enrollment.getId()).orElseThrow().getId();
        return new Learner(child.getId(), pathId);
    }

    private UUID recognitionSkillId() {
        return skillRepository.findByCode(RECOGNITION_SKILL).orElseThrow().getId();
    }

    private record Learner(UUID childId, UUID pathId) {
    }

}
