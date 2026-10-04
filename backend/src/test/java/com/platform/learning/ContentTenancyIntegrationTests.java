package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.Lesson;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.LessonRepository;
import com.platform.content.service.ActivityService;
import com.platform.content.service.LessonService;
import com.platform.content.service.QuestionService;
import com.platform.content.studio.StudioDtos.CreateCurriculumRequest;
import com.platform.content.studio.StudioDtos.CreateLessonRequest;
import com.platform.content.studio.StudioService;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.curriculum.web.CurriculumController;
import com.platform.curriculum.web.CurriculumDtos.CurriculumSummaryResponse;
import com.platform.family.entity.Family;
import com.platform.family.repository.FamilyRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillService;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.repository.LearningPathRepository;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.EnrollmentService;
import com.platform.learning.service.LearningPathService;
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
 * N7 against real PostgreSQL: a teacher's content never reaches a family child's
 * learning path - not through enrollment seeding, the Adaptive Engine, Smart Review,
 * free practice or the family curriculum catalog - while a curriculum the teacher
 * owns still draws on the teacher's own content.
 *
 * <p>The teacher's activity is made the one every selector would prefer (the easiest,
 * so reinforcement and review would pick it; the hardest, so a challenge would), so an
 * unscoped selector would place it and the test would see it.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class ContentTenancyIntegrationTests {

    @Autowired private AccountRepository accountRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private CurriculumService curriculumService;
    @Autowired private CurriculumController curriculumController;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private SkillService skillService;
    @Autowired private LessonService lessonService;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private QuestionService questionService;
    @Autowired private ActivityService activityService;
    @Autowired private ActivityItemRepository activityItemRepository;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private LearningPathRepository learningPathRepository;
    @Autowired private LearningPathService learningPathService;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private StudioService studioService;

    @Test
    void aFamilyPathIsSeededWithPlatformContentOnly() {
        Catalog catalog = catalog();
        Learner learner = familyLearner(catalog.platformCurriculum());

        assertThat(activityVersionIdsOnPath(learner))
                .contains(catalog.platformEasy().getId(), catalog.platformHard().getId())
                .doesNotContain(catalog.teacherEasiest().getId(), catalog.teacherHardest().getId());
    }

    @Test
    void reinforcementAndReviewOnAFamilyPathNeverPlaceTeacherContent() {
        Catalog catalog = catalog();
        Learner learner = familyLearner(catalog.platformCurriculum());

        // Fail every platform activity: low mastery with enough evidence drives both
        // REMEDIATE (the Adaptive Engine) and a Smart Review flag and placement.
        playEverythingOnPath(learner, false);

        assertThat(activityVersionIdsOnPath(learner))
                .doesNotContain(catalog.teacherEasiest().getId(), catalog.teacherHardest().getId());
    }

    @Test
    void aChallengeOnAFamilyPathNeverPlacesTeacherContent() {
        Catalog catalog = catalog();
        Learner learner = familyLearner(catalog.platformCurriculum());

        playEverythingOnPath(learner, true);

        assertThat(activityVersionIdsOnPath(learner))
                .doesNotContain(catalog.teacherEasiest().getId(), catalog.teacherHardest().getId());
    }

    @Test
    void aFamilyCannotEnrollInOrSeeATeacherCurriculum() {
        Catalog catalog = catalog();
        Child child = familyChild();

        assertThatThrownBy(() -> enrollmentService.enrollInPlatformCurriculum(child.getId(), catalog.teacherCurriculum().getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(curriculumController.listCurricula().getBody())
                .extracting(CurriculumSummaryResponse::id)
                .contains(catalog.platformCurriculum().getId())
                .doesNotContain(catalog.teacherCurriculum().getId());
    }

    @Test
    void freePracticeOfTeacherContentIsRefusedOutsideTheTeachersCurriculum() {
        Catalog catalog = catalog();
        Learner family = familyLearner(catalog.platformCurriculum());
        Learner classroom = classLearner(catalog.teacherCurriculum());

        assertThatThrownBy(() -> activityAttemptService.startAttempt(family.childId(), catalog.teacherEasiest().getId(), null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(activityAttemptService.startAttempt(family.childId(), catalog.platformEasy().getId(), null, null)).isNotNull();
        assertThat(activityAttemptService.startAttempt(classroom.childId(), catalog.teacherEasiest().getId(), null, null)).isNotNull();
    }

    @Test
    void aTeacherCurriculumDrawsOnItsOwnersContentAndPlatformContent() {
        Catalog catalog = catalog();
        Learner learner = classLearner(catalog.teacherCurriculum());

        assertThat(activityVersionIdsOnPath(learner))
                .contains(catalog.teacherEasiest().getId(), catalog.teacherHardest().getId(),
                        catalog.platformEasy().getId(), catalog.platformHard().getId());
    }

    @Test
    void anotherTeachersContentStaysOutOfATeacherCurriculum() {
        Catalog catalog = catalog();
        Account otherTeacher = teacher();
        Lesson otherLesson = lessonService.createOwnedLesson(catalog.subjectId(), "Other teacher", otherTeacher.getId());
        ActivityVersion foreign = activity(catalog, otherLesson.getId(), "Other teacher", 1);

        Learner learner = classLearner(catalog.teacherCurriculum());

        assertThat(activityVersionIdsOnPath(learner)).doesNotContain(foreign.getId());
    }

    @Test
    void contentAnAdminAuthorsIsPlatformContent() {
        Catalog catalog = catalog();
        Account admin = accountRepository.save(Account.register(
                "admin-" + UUID.randomUUID() + "@example.test", "x".repeat(60), PlatformRole.ADMIN));

        // Through the Studio, the one authoring API (TD-19).
        var lesson = studioService.createLesson(admin.getId(), new CreateLessonRequest(catalog.subjectId(), "Admin lesson"));
        var curriculum = studioService.createCurriculum(admin.getId(),
                new CreateCurriculumRequest(catalog.subjectId(), catalog.ageHubId(), "Admin curriculum"));

        assertThat(lessonRepository.findById(lesson.id()).orElseThrow().getOwnerAccountId()).isNull();
        assertThat(curriculumService.getCurriculum(curriculum.id()).getOwnerAccountId()).isNull();
    }

    // ------------------------------------------------------------------ fixture

    /**
     * One skill. Platform content: a platform lesson with an easy and a harder activity,
     * in a platform curriculum. Teacher content: the teacher's lesson with the easiest and
     * the hardest activity for the same skill, in the teacher's own curriculum.
     */
    private Catalog catalog() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("TENANCY_" + suffix, "Tenancy " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("TENANCY_" + suffix, "Tenancy " + suffix, 90, 99, 99)).getId();
        Skill skill = skillService.createSkill(subjectId, "TENANCY_" + suffix, "Tenancy " + suffix, null);
        LearningObjective objective = learningObjectiveService.create(subjectId, "TENANCY_OBJ_" + suffix, "Tenancy " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());

        Curriculum platform = curriculumService.createCurriculum(subjectId, ageHubId, "Platform " + suffix);
        Account teacher = teacher();
        Curriculum teacherCurriculum = curriculumService.createOwnedCurriculum(subjectId, ageHubId, "Teacher " + suffix, teacher.getId());

        Catalog partial = new Catalog(subjectId, ageHubId, skill, platform, teacherCurriculum, null, null, null, null);
        Lesson platformLesson = lessonService.createLesson(subjectId, "Platform lesson " + suffix);
        Lesson teacherLesson = lessonService.createOwnedLesson(subjectId, "Teacher lesson " + suffix, teacher.getId());
        ActivityVersion platformEasy = activity(partial, platformLesson.getId(), "Platform easy", 3);
        ActivityVersion platformHard = activity(partial, platformLesson.getId(), "Platform hard", 5);
        ActivityVersion teacherEasiest = activity(partial, teacherLesson.getId(), "Teacher easiest", 1);
        ActivityVersion teacherHardest = activity(partial, teacherLesson.getId(), "Teacher hardest", 10);
        // Published last: a curriculum needs a published activity first (ContentValidator).
        publishCurriculum(platform, objective);
        publishCurriculum(teacherCurriculum, objective);

        return new Catalog(subjectId, ageHubId, skill, platform, teacherCurriculum, platformEasy, platformHard,
                teacherEasiest, teacherHardest);
    }

    private void publishCurriculum(Curriculum curriculum, LearningObjective objective) {
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
    }

    private Account teacher() {
        return accountRepository.save(Account.register(
                "teacher-" + UUID.randomUUID() + "@example.test", "x".repeat(60), PlatformRole.TEACHER));
    }

    /** A published activity of three numeric questions for the catalog's skill. */
    private ActivityVersion activity(Catalog catalog, UUID lessonId, String title, int difficulty) {
        Activity activity = activityService.createActivity(catalog.subjectId(), lessonId, "PRACTICE", title);
        ActivityVersion version = activityService.createDraftVersion(activity.getId(), "Add", difficulty, 60);
        activityService.tagSkill(version.getId(), catalog.skill().getId());
        for (int i = 0; i < 3; i++) {
            Question question = questionService.createQuestion(catalog.subjectId(), QuestionType.NUMERIC);
            QuestionVersion draft = questionService.createDraftVersion(question.getId(), i + " + 1 = ?", String.valueOf(i + 1), null);
            questionService.tagSkill(draft.getId(), catalog.skill().getId());
            QuestionVersion questionVersion = questionService.publish(draft.getId());
            activityService.addItem(version.getId(), questionVersion.getId(), i, BigDecimal.ONE);
        }
        return activityService.publish(version.getId());
    }

    private Child familyChild() {
        Family family = familyRepository.save(Family.create("Tenancy Family"));
        return childRepository.save(Child.enroll(family.getId(), "Tenancy Child", LocalDate.of(2018, 6, 1)));
    }

    private Learner familyLearner(Curriculum curriculum) {
        Child child = familyChild();
        Enrollment enrollment = enrollmentService.enrollInPlatformCurriculum(child.getId(), curriculum.getId());
        return new Learner(child.getId(), learningPathRepository.findByEnrollmentId(enrollment.getId()).orElseThrow().getId());
    }

    /** The class route: a teacher's curriculum assigned to a class enrolls through EnrollmentService#enroll. */
    private Learner classLearner(Curriculum curriculum) {
        Child child = familyChild();
        Enrollment enrollment = enrollmentService.enroll(child.getId(), curriculum.getId());
        return new Learner(child.getId(), learningPathRepository.findByEnrollmentId(enrollment.getId()).orElseThrow().getId());
    }

    /** Plays the path until nothing is pending (bounded), answering every question right or wrong. */
    private void playEverythingOnPath(Learner learner, boolean correct) {
        for (int step = 0; step < 12; step++) {
            LearningPathItem item;
            try {
                item = learningPathService.getNextItem(learner.pathId());
            } catch (ResourceNotFoundException caughtUp) {
                return;
            }
            ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
            for (var activityItem : activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(item.getActivityVersionId())) {
                String answer = correct ? questionService.getVersion(activityItem.getQuestionVersionId()).getCorrectAnswer() : "999";
                activityAttemptService.submitAnswer(attempt.getId(), activityItem.getQuestionVersionId(), answer, 3,
                        UUID.randomUUID().toString());
            }
            activityAttemptService.completeAttempt(attempt.getId());
        }
    }

    private List<UUID> activityVersionIdsOnPath(Learner learner) {
        return learningPathService.listItems(learner.pathId()).stream().map(LearningPathItem::getActivityVersionId).toList();
    }

    private record Catalog(UUID subjectId, UUID ageHubId, Skill skill, Curriculum platformCurriculum,
                           Curriculum teacherCurriculum, ActivityVersion platformEasy, ActivityVersion platformHard,
                           ActivityVersion teacherEasiest, ActivityVersion teacherHardest) {
    }

    private record Learner(UUID childId, UUID pathId) {
    }

}
