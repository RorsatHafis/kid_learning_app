package com.platform.content;

import com.platform.TestcontainersConfiguration;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import com.platform.content.web.ActivityContentController;
import com.platform.content.web.ActivityContentDtos.ActivityItemResponse;
import com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.family.entity.Family;
import com.platform.family.repository.FamilyRepository;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillService;
import com.platform.learner.repository.AdaptationDecisionRepository;
import com.platform.learner.repository.LearnerSkillHistoryRepository;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.repository.LearningPathRepository;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.EnrollmentService;
import com.platform.learning.service.LearningPathService;
import com.platform.review.service.SmartReviewService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Battle 3R against real PostgreSQL and the real services: publishing a new content
 * version supersedes (RETIRES) the old one without stranding any child who already
 * holds a reference to it. New work always uses the current PUBLISHED version,
 * existing references keep their exact pinned version, and DRAFT content is never
 * learner-visible.
 *
 * <p>Each test builds its own subject, age hub, skill, objective, curriculum and content, so it neither
 * depends on nor disturbs the seeded catalog other tests use.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class ContentLifecycleIntegrationTests {

    @Autowired private FamilyRepository familyRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private CurriculumService curriculumService;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private SkillService skillService;
    @Autowired private QuestionService questionService;
    @Autowired private ActivityService activityService;
    @Autowired private ActivityVersionRepository activityVersionRepository;
    @Autowired private ActivityContentController activityContentController;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private LearningPathRepository learningPathRepository;
    @Autowired private LearningPathService learningPathService;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private SmartReviewService smartReviewService;
    @Autowired private LearnerSkillHistoryRepository learnerSkillHistoryRepository;
    @Autowired private AdaptationDecisionRepository adaptationDecisionRepository;

    // ------------------------------------------------------------------ lifecycle basics

    @Test
    void draftContentIsNeverLearnerVisible() {
        Catalog catalog = catalog();
        ActivityVersion draft = activityService.createDraftVersion(catalog.first().getId(), "Draft", 1, 60);
        activityService.addItem(draft.getId(), catalog.q1v1().getId(), 0, BigDecimal.ONE);
        QuestionVersion draftQuestion = questionService.createDraftVersion(catalog.q1().getId(), "draft prompt", "2", null);
        Learner learner = enroll(catalog);

        assertThatThrownBy(() -> activityContentController.getActivityVersion(draft.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> questionService.getLearnerVisibleVersionById(draftQuestion.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> activityAttemptService.startAttempt(learner.childId(), draft.getId(), null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void newWorkUsesTheCurrentPublishedVersion() {
        Catalog catalog = catalog();
        ActivityVersion v2 = republishFirstActivity(catalog, catalog.q1v1(), catalog.q2v1());

        Learner newcomer = enroll(catalog);

        assertThat(activityVersionIdsOnPath(newcomer)).contains(v2.getId()).doesNotContain(catalog.firstV1().getId());
        // A start without path context is new work: only the current version may be started that way.
        assertThatThrownBy(() -> activityAttemptService.startAttempt(newcomer.childId(), catalog.firstV1().getId(), null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(activityAttemptService.startAttempt(newcomer.childId(), v2.getId(), null, null).getActivityVersionId())
                .isEqualTo(v2.getId());
    }

    // ------------------------------------------------------------------ L5: activity republished under an existing path

    @Test
    void anExistingPathItemStaysPlayableAfterItsActivityIsRepublished() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        republishFirstActivity(catalog, catalog.q1v1(), catalog.q2v1());
        assertThat(activityVersionRepository.findById(catalog.firstV1().getId()).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.RETIRED);

        LearningPathItem next = learningPathService.getNextItem(learner.pathId());
        assertThat(next.getActivityVersionId()).isEqualTo(catalog.firstV1().getId());

        ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), next.getActivityVersionId(), next.getId(), null);
        ActivityVersionDetailResponse content = activityContentController.getActivityVersion(next.getActivityVersionId()).getBody();
        assertThat(content.items()).extracting(ActivityItemResponse::questionVersionId)
                .containsExactly(catalog.q1v1().getId(), catalog.q2v1().getId());
        answerAll(attempt, content, true);
        ActivityAttempt completed = activityAttemptService.completeAttempt(attempt.getId());

        assertThat(completed.getScore()).isEqualByComparingTo("100");
        assertThat(learningPathService.getItem(next.getId()).getStatus()).isEqualTo(LearningPathItemStatus.COMPLETED);
        LearningPathItem after = learningPathService.getNextItem(learner.pathId());
        assertThat(after.getId()).isNotEqualTo(next.getId());
        assertThat(after.getActivityVersionId()).isEqualTo(catalog.secondV1().getId());
        assertThat(activityAttemptService.startAttempt(learner.childId(), after.getActivityVersionId(), after.getId(), null)).isNotNull();
    }

    @Test
    void anInProgressAttemptResumesOnItsOriginalVersionAndIsNeverSwitched() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        LearningPathItem item = learningPathService.getNextItem(learner.pathId());
        ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
        activityAttemptService.submitAnswer(attempt.getId(), catalog.q1v1().getId(), "2", 3, UUID.randomUUID().toString());

        QuestionVersion q1v2 = republishQuestion(catalog.q1(), "1 + 1 = ? (v2)", "2");
        republishFirstActivity(catalog, q1v2, catalog.q2v1());

        ActivityAttempt resumed = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
        assertThat(resumed.getId()).isEqualTo(attempt.getId());
        assertThat(resumed.getActivityVersionId()).isEqualTo(catalog.firstV1().getId());
        assertThat(activityContentController.getActivityVersion(resumed.getActivityVersionId()).getBody().items())
                .extracting(ActivityItemResponse::questionVersionId)
                .containsExactly(catalog.q1v1().getId(), catalog.q2v1().getId());
        // The attempt stays on its own questions: the new question version is not part of it.
        assertThatThrownBy(() -> activityAttemptService.submitAnswer(attempt.getId(), q1v2.getId(), "2", 3, UUID.randomUUID().toString()))
                .isInstanceOf(ResourceNotFoundException.class);

        activityAttemptService.submitAnswer(attempt.getId(), catalog.q2v1().getId(), "3", 3, UUID.randomUUID().toString());
        ActivityAttempt completed = activityAttemptService.completeAttempt(attempt.getId());
        assertThat(completed.getStatus()).isEqualTo(ActivityAttemptStatus.COMPLETED);
        assertThat(completed.getScore()).isEqualByComparingTo("100");
    }

    // ------------------------------------------------------------------ L4: question republished under a published activity

    @Test
    void aSupersededQuestionPinnedByAPublishedActivityIsStillServedAndAnswerable() {
        Catalog catalog = catalog();
        Learner existing = enroll(catalog);
        republishQuestion(catalog.q1(), "1 + 1 = ? (v2)", "2");

        // The activity itself was not republished: its current version still pins q1 v1.
        assertThat(activityVersionRepository.findById(catalog.firstV1().getId()).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.PUBLISHED);
        Learner newcomer = enroll(catalog);
        for (Learner learner : List.of(existing, newcomer)) {
            LearningPathItem item = learningPathService.getNextItem(learner.pathId());
            ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
            ActivityVersionDetailResponse content = activityContentController.getActivityVersion(item.getActivityVersionId()).getBody();
            assertThat(content.items().get(0).prompt()).isEqualTo("1 + 1 = ?");
            answerAll(attempt, content, true);
            assertThat(activityAttemptService.completeAttempt(attempt.getId()).getScore()).isEqualByComparingTo("100");
        }
    }

    @Test
    void aNewActivityVersionCannotBePublishedOnASupersededQuestion() {
        Catalog catalog = catalog();
        republishQuestion(catalog.q1(), "1 + 1 = ? (v2)", "2");
        ActivityVersion draft = activityService.createDraftVersion(catalog.first().getId(), "Stale", 1, 60);
        activityService.addItem(draft.getId(), catalog.q1v1().getId(), 0, BigDecimal.ONE);

        assertThatThrownBy(() -> activityService.publish(draft.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(activityVersionRepository.findById(catalog.firstV1().getId()).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.PUBLISHED);
    }

    // ------------------------------------------------------------------ history

    @Test
    void aCompletedAttemptRemainsFullyReconstructableAfterRepublishing() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        LearningPathItem item = learningPathService.getNextItem(learner.pathId());
        ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
        activityAttemptService.submitAnswer(attempt.getId(), catalog.q1v1().getId(), "2", 3, UUID.randomUUID().toString());
        activityAttemptService.submitAnswer(attempt.getId(), catalog.q2v1().getId(), "999", 3, UUID.randomUUID().toString());
        activityAttemptService.completeAttempt(attempt.getId());
        int evidenceBefore = learnerSkillHistoryRepository.findByChildIdAndSkillIdOrderByOccurredAtDesc(learner.childId(), catalog.skill().getId()).size();

        QuestionVersion q1v2 = republishQuestion(catalog.q1(), "1 + 1 = ? (v2)", "2");
        QuestionVersion q2v2 = republishQuestion(catalog.q2(), "2 + 1 = ? (v2)", "3");
        republishFirstActivity(catalog, q1v2, q2v2);

        ActivityAttempt history = activityAttemptService.getAttempt(attempt.getId());
        assertThat(history.getActivityVersionId()).isEqualTo(catalog.firstV1().getId());
        assertThat(history.getScore()).isEqualByComparingTo("50");
        List<AnswerRecord> answers = activityAttemptService.listAnswers(attempt.getId());
        assertThat(answers).extracting(AnswerRecord::getQuestionVersionId)
                .containsExactlyInAnyOrder(catalog.q1v1().getId(), catalog.q2v1().getId());
        ActivityVersionDetailResponse content = activityContentController.getActivityVersion(history.getActivityVersionId()).getBody();
        assertThat(content.items()).extracting(ActivityItemResponse::prompt).containsExactly("1 + 1 = ?", "2 + 1 = ?");
        assertThat(learnerSkillHistoryRepository.findByChildIdAndSkillIdOrderByOccurredAtDesc(learner.childId(), catalog.skill().getId()))
                .hasSize(evidenceBefore);
    }

    // ------------------------------------------------------------------ adaptive / smart review

    @Test
    void adaptiveAndSmartReviewOnlyPlaceUsableCurrentVersions() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        QuestionVersion q1v2 = republishQuestion(catalog.q1(), "1 + 1 = ? (v2)", "2");
        ActivityVersion firstV2 = republishFirstActivity(catalog, q1v2, catalog.q2v1());
        republishQuestion(catalog.q2(), "2 + 1 = ? (v2)", "3"); // secondV1 stays current but pins a superseded question

        // The child plays their existing (now superseded) placement. Since Battle 5D the
        // adaptive and review decisions are made by completing it, so they are selections
        // made after the republish.
        LearningPathItem item = learningPathService.getNextItem(learner.pathId());
        assertThat(item.getActivityVersionId()).isEqualTo(catalog.firstV1().getId());
        ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
        answerAll(attempt, activityContentController.getActivityVersion(item.getActivityVersionId()).getBody(), false);
        activityAttemptService.completeAttempt(attempt.getId());

        LearningPathItem adaptive = learningPathService.getItem(
                adaptationDecisionRepository.findByActivityAttemptId(attempt.getId()).orElseThrow().getLearningPathItemId());
        LearningPathItem review = smartReviewService.findPendingReviewOnPath(learner.pathId()).orElseThrow();

        for (LearningPathItem placed : List.of(adaptive, review)) {
            ActivityVersion version = activityVersionRepository.findById(placed.getActivityVersionId()).orElseThrow();
            assertThat(version.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
            assertThat(version.getId()).isIn(firstV2.getId(), catalog.secondV1().getId());
            ActivityVersionDetailResponse content = activityContentController.getActivityVersion(version.getId()).getBody();
            assertThat(content.items()).isNotEmpty();
            ActivityAttempt placedAttempt = activityAttemptService.startAttempt(learner.childId(), version.getId(), placed.getId(), null);
            answerAll(placedAttempt, content, true);
            assertThat(activityAttemptService.completeAttempt(placedAttempt.getId()).getStatus()).isEqualTo(ActivityAttemptStatus.COMPLETED);
        }
    }

    // ------------------------------------------------------------------ fixture

    /** Its own skill, objective and published curriculum; two activities (easy, then harder) of two numeric questions each. */
    private Catalog catalog() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // Its own subject and an age hub no test child falls into, so the seeded catalog,
        // curriculum lookups and age-based auto-enrollment elsewhere are unaffected.
        UUID subjectId = subjectRepository.save(Subject.create("LIFECYCLE_" + suffix, "Lifecycle " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("LIFECYCLE_" + suffix, "Lifecycle " + suffix, 90, 99, 99)).getId();

        Skill skill = skillService.createSkill(subjectId, "LIFECYCLE_" + suffix, "Lifecycle " + suffix, null);
        LearningObjective objective = learningObjectiveService.create(subjectId, "LIFECYCLE_OBJ_" + suffix, "Lifecycle " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Lifecycle " + suffix);
        var curriculumVersion = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(curriculumVersion.getId(), objective.getId(), 0);
        curriculumService.publish(curriculumVersion.getId());

        Question q1 = numericQuestion(subjectId, skill);
        QuestionVersion q1v1 = republishQuestion(q1, "1 + 1 = ?", "2");
        Question q2 = numericQuestion(subjectId, skill);
        QuestionVersion q2v1 = republishQuestion(q2, "2 + 1 = ?", "3");
        Question q3 = numericQuestion(subjectId, skill);
        QuestionVersion q3v1 = republishQuestion(q3, "3 + 1 = ?", "4");

        Activity first = activityService.createActivity(subjectId, null, "PRACTICE", "Lifecycle first " + suffix);
        activityService.tagSkill(first.getId(), skill.getId());
        ActivityVersion firstV1 = publishActivityVersion(first, 1, q1v1, q2v1);
        Activity second = activityService.createActivity(subjectId, null, "PRACTICE", "Lifecycle second " + suffix);
        activityService.tagSkill(second.getId(), skill.getId());
        ActivityVersion secondV1 = publishActivityVersion(second, 2, q2v1, q3v1);

        return new Catalog(curriculum, skill, q1, q1v1, q2, q2v1, first, firstV1, secondV1);
    }

    private Question numericQuestion(UUID subjectId, Skill skill) {
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        questionService.tagSkill(question.getId(), skill.getId());
        return question;
    }

    private QuestionVersion republishQuestion(Question question, String prompt, String answer) {
        QuestionVersion version = questionService.createDraftVersion(question.getId(), prompt, answer, null);
        return questionService.publish(version.getId());
    }

    private ActivityVersion publishActivityVersion(Activity activity, int difficulty, QuestionVersion... questions) {
        ActivityVersion version = activityService.createDraftVersion(activity.getId(), "Add the numbers", difficulty, 60);
        for (int i = 0; i < questions.length; i++) {
            activityService.addItem(version.getId(), questions[i].getId(), i, BigDecimal.ONE);
        }
        return activityService.publish(version.getId());
    }

    private ActivityVersion republishFirstActivity(Catalog catalog, QuestionVersion... questions) {
        return publishActivityVersion(catalog.first(), 1, questions);
    }

    private Learner enroll(Catalog catalog) {
        Family family = familyRepository.save(Family.create("Lifecycle Family"));
        Child child = childRepository.save(Child.enroll(family.getId(), "Lifecycle Child", LocalDate.of(2018, 6, 1)));
        Enrollment enrollment = enrollmentService.enroll(child.getId(), catalog.curriculum().getId());
        return new Learner(child.getId(), learningPathRepository.findByEnrollmentId(enrollment.getId()).orElseThrow().getId());
    }

    private List<UUID> activityVersionIdsOnPath(Learner learner) {
        return learningPathService.listItems(learner.pathId()).stream().map(LearningPathItem::getActivityVersionId).toList();
    }

    /** Answers from the served content: the pinned question versions' own answer keys. */
    private void answerAll(ActivityAttempt attempt, ActivityVersionDetailResponse content, boolean correct) {
        for (ActivityItemResponse item : content.items()) {
            String answer = correct ? questionService.getVersion(item.questionVersionId()).getCorrectAnswer() : "999";
            activityAttemptService.submitAnswer(attempt.getId(), item.questionVersionId(), answer, 3, UUID.randomUUID().toString());
        }
    }

    private record Catalog(Curriculum curriculum, Skill skill, Question q1, QuestionVersion q1v1, Question q2,
                           QuestionVersion q2v1, Activity first, ActivityVersion firstV1, ActivityVersion secondV1) {
    }

    private record Learner(UUID childId, UUID pathId) {
    }

}
