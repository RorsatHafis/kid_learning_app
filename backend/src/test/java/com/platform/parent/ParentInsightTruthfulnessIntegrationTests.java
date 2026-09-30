package com.platform.parent;

import com.platform.TestcontainersConfiguration;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.family.entity.Family;
import com.platform.family.repository.FamilyRepository;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.learner.entity.LearnerObjectiveState;
import com.platform.learner.entity.LearnerObjectiveStatus;
import com.platform.learner.repository.LearnerObjectiveStateRepository;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.EnrollmentService;
import com.platform.parent.entity.ParentInsight;
import com.platform.parent.entity.ParentInsightType;
import com.platform.parent.service.ParentInsightService;
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
 * Battle 5C against real PostgreSQL (Flyway through V35, real services, seeded
 * content whose objective maps 4 skills): objective state and Parent Insight are
 * derived from the evidence the child actually produced, and a recorded snapshot
 * is history - never presented as the child's current state.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class ParentInsightTruthfulnessIntegrationTests {

    private static final String RECOGNITION_SKILL = "MATHS_ADD_RECOGNITION";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private CurriculumRepository curriculumRepository;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private SkillRepository skillRepository;
    @Autowired private ActivityVersionSkillRepository activitySkillRepository;
    @Autowired private ActivityVersionRepository activityVersionRepository;
    @Autowired private ActivityItemRepository activityItemRepository;
    @Autowired private QuestionVersionRepository questionVersionRepository;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private LearnerObjectiveStateRepository learnerObjectiveStateRepository;
    @Autowired private ParentInsightService parentInsightService;

    @Test
    void v35RanWithItsConfigurationPlaceholdersResolved() {
        Boolean success = jdbc.queryForObject(
                "select success from flyway_schema_history where version = '35'", Boolean.class);
        assertThat(success).isTrue();
    }

    @Test
    void perfectEvidenceOnOneOfFourSkillsIsProgressNotWeaknessOrMastery() {
        UUID child = enroll("Perfect Partial");
        practiseRecognition(child, true, 2); // 4 correct answers on 1 of the objective's 4 skills

        LearnerObjectiveState objective = onlyObjective(child);
        assertThat(objective.getMasteryProbability()).isEqualByComparingTo("1.0000"); // was 0.2500
        assertThat(objective.getStatus()).isEqualTo(LearnerObjectiveStatus.IN_PROGRESS);

        List<ParentInsight> current = parentInsightService.currentInsights(child);
        assertThat(current).extracting(ParentInsight::getInsightType).containsExactly(ParentInsightType.PROGRESS);
    }

    @Test
    void allWrongOnOneOfFourSkillsIsWeaknessAndTheObjectiveIsNotNotStarted() {
        UUID child = enroll("Struggling Partial");
        practiseRecognition(child, false, 2);

        LearnerObjectiveState objective = onlyObjective(child);
        assertThat(objective.getStatus()).isEqualTo(LearnerObjectiveStatus.IN_PROGRESS); // was NOT_STARTED

        assertThat(parentInsightService.currentInsights(child))
                .extracting(ParentInsight::getInsightType)
                .contains(ParentInsightType.WEAKNESS);
    }

    @Test
    void aRecordedSnapshotIsHistoryAndNeverShownAsTheCurrentState() {
        UUID child = enroll("Improving");
        practiseRecognition(child, false, 2);
        List<ParentInsight> snapshot = parentInsightService.generateInsights(child);
        assertThat(snapshot).extracting(ParentInsight::getInsightType).contains(ParentInsightType.WEAKNESS);

        practiseRecognition(child, true, 4); // the child improves: 4 wrong, then 8 right

        List<ParentInsight> current = parentInsightService.currentInsights(child);
        assertThat(current).extracting(ParentInsight::getInsightType)
                .contains(ParentInsightType.PROGRESS)
                .doesNotContain(ParentInsightType.WEAKNESS);

        // History keeps the earlier claim as an event - nothing deleted, nothing rewritten.
        assertThat(parentInsightService.listRecent(child))
                .extracting(ParentInsight::getInsightType)
                .contains(ParentInsightType.WEAKNESS);
    }

    /** {@code attempts} completed free-practice attempts of the seeded Recognition activity (2 questions each). */
    private void practiseRecognition(UUID childId, boolean correct, int attempts) {
        UUID skillId = skillRepository.findByCode(RECOGNITION_SKILL).orElseThrow().getId();
        ActivityVersion version = activitySkillRepository.findPublishedPractising(skillId).stream()
                .findFirst().orElseThrow();
        List<ActivityItem> items = activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(version.getId());

        for (int n = 0; n < attempts; n++) {
            ActivityAttempt attempt = activityAttemptService.startAttempt(childId, version.getId(), null, null);
            for (ActivityItem item : items) {
                String correctAnswer = questionVersionRepository.findById(item.getQuestionVersionId()).orElseThrow().getCorrectAnswer();
                String submitted = correct ? correctAnswer : new BigDecimal(correctAnswer).add(BigDecimal.valueOf(1000)).toPlainString();
                activityAttemptService.submitAnswer(attempt.getId(), item.getQuestionVersionId(), submitted, 5, UUID.randomUUID().toString());
            }
            activityAttemptService.completeAttempt(attempt.getId());
        }
    }

    private UUID enroll(String name) {
        Family family = familyRepository.save(Family.create(name + "'s Family"));
        Child child = childRepository.save(Child.enroll(family.getId(), name, LocalDate.of(2018, 6, 1)));
        Curriculum maths = curriculumRepository.findByStatus(CatalogStatus.ACTIVE).stream()
                .filter(c -> c.getName().equals("Maths - Junior")).findFirst().orElseThrow();
        enrollmentService.enroll(child.getId(), maths.getId());
        return child.getId();
    }

    private LearnerObjectiveState onlyObjective(UUID childId) {
        List<LearnerObjectiveState> states = learnerObjectiveStateRepository.findByChildId(childId);
        assertThat(states).hasSize(1);
        return states.get(0);
    }

}
