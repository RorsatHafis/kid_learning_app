package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
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
import com.platform.learner.entity.AdaptationDecision;
import com.platform.learner.entity.AdaptationDecisionType;
import com.platform.learner.repository.AdaptationDecisionRepository;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.repository.LearningPathRepository;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.EnrollmentService;
import com.platform.learning.service.LearningPathService;
import com.platform.learning.service.PostCompletionService;
import com.platform.review.entity.ReviewItem;
import com.platform.review.entity.ReviewItemStatus;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Battle 5D against real PostgreSQL: completing an attempt is what produces the
 * child's post-completion learning state (review evaluation, one adaptive decision,
 * path placements). No client follow-up call is needed, and none can duplicate it.
 * Covers retries, concurrent completion, the Smart Review find-or-create race (N2)
 * and multi-skill activities (L7).
 *
 * <p>Each test builds its own subject, age hub, skills, curriculum and content, so it
 * neither depends on nor disturbs the seeded catalog.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class LearningLoopCompletionIntegrationTests {

    @Autowired private JdbcTemplate jdbc;
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
    @Autowired private ActivityItemRepository activityItemRepository;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private LearningPathRepository learningPathRepository;
    @Autowired private LearningPathService learningPathService;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private PostCompletionService postCompletionService;
    @Autowired private SmartReviewService smartReviewService;
    @Autowired private AdaptationDecisionRepository adaptationDecisionRepository;

    // ------------------------------------------------------------------ completion alone is enough

    @Test
    void completingAloneProducesTheWholePostCompletionState() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        LearningPathItem item = itemFor(learner, catalog.easy());

        ActivityAttempt attempt = play(learner, item, false);

        // Nothing but start/answer/complete was called. Read everything back as a reload would.
        assertThat(activityAttemptService.getAttempt(attempt.getId()).getStatus()).isEqualTo(ActivityAttemptStatus.COMPLETED);
        assertThat(learningPathService.getItem(item.getId()).getStatus()).isEqualTo(LearningPathItemStatus.COMPLETED);
        assertThat(evidenceCount(learner, catalog.s1())).isEqualTo(2);

        AdaptationDecision decision = adaptationDecisionRepository.findByActivityAttemptId(attempt.getId()).orElseThrow();
        assertThat(decision.getDecisionType()).isEqualTo(AdaptationDecisionType.REMEDIATE);
        assertThat(decision.getContext()).containsEntry("governingSkillId", catalog.s1().getId().toString());
        assertThat(decision.getLearningPathItemId()).isNotNull();

        assertThat(smartReviewService.findItem(learner.childId(), catalog.s1().getId()))
                .hasValueSatisfying(review -> assertThat(review.getStatus()).isEqualTo(ReviewItemStatus.ACTIVE));
        assertThat(pending(learner, LearningPathItemSource.REVIEW)).hasSize(1);
        // The next learning state exists and is playable.
        assertThat(learningPathService.getNextItem(learner.pathId())).isNotNull();
    }

    @Test
    void freePracticeRecordsEvidenceButMakesNoPathDecisions() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        int itemsBefore = learningPathService.listItems(learner.pathId()).size();

        ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), catalog.easy().getId(), null, null);
        answerAll(attempt, false);
        activityAttemptService.completeAttempt(attempt.getId());

        assertThat(evidenceCount(learner, catalog.s1())).isEqualTo(2);
        assertThat(adaptationDecisionRepository.findByActivityAttemptId(attempt.getId())).isEmpty();
        assertThat(learningPathService.listItems(learner.pathId())).hasSize(itemsBefore);
    }

    // ------------------------------------------------------------------ retries and concurrency

    @Test
    void retriedCompletionChangesNothing() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        ActivityAttempt attempt = play(learner, itemFor(learner, catalog.easy()), false);
        Snapshot before = snapshot(learner);

        ActivityAttempt again = activityAttemptService.completeAttempt(attempt.getId());
        ActivityAttempt andAgain = activityAttemptService.completeAttempt(attempt.getId());

        assertThat(again.getScore()).isEqualByComparingTo(attempt.getScore());
        // The retries return the stored attempt. PostgreSQL rounds to microseconds, and the
        // first call returned the in-memory instant, so that one may differ by under 1 us.
        assertThat(andAgain.getCompletedAt()).isEqualTo(again.getCompletedAt());
        assertThat(Duration.between(attempt.getCompletedAt(), again.getCompletedAt()).abs()).isLessThan(Duration.ofNanos(1_000));
        assertThat(snapshot(learner)).isEqualTo(before);
    }

    @Test
    void concurrentCompletionOfOneAttemptRunsItsDecisionsOnce() throws Exception {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        LearningPathItem item = itemFor(learner, catalog.easy());
        ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
        answerAll(attempt, false);
        int itemsBefore = learningPathService.listItems(learner.pathId()).size();

        List<ActivityAttempt> results = concurrently(8, () -> activityAttemptService.completeAttempt(attempt.getId()));

        assertThat(results).allSatisfy(result -> assertThat(result.getStatus()).isEqualTo(ActivityAttemptStatus.COMPLETED));
        Instant stored = activityAttemptService.getAttempt(attempt.getId()).getCompletedAt();
        // One completion happened: every caller got that same instant (the winner's in-memory
        // copy may differ from the stored, microsecond-rounded one by under 1 us).
        assertThat(results).allSatisfy(result ->
                assertThat(Duration.between(result.getCompletedAt(), stored).abs()).isLessThan(Duration.ofNanos(1_000)));
        assertThat(count("select count(*) from adaptation_decisions where activity_attempt_id = ?", attempt.getId())).isEqualTo(1);
        assertThat(count("select count(*) from review_items where child_id = ?", learner.childId())).isEqualTo(1);
        assertThat(pending(learner, LearningPathItemSource.REVIEW)).hasSize(1);
        // At most one review placement from this single completion; the adaptive decision
        // reused the harder activity already waiting on the curriculum path.
        assertThat(learningPathService.listItems(learner.pathId())).hasSize(itemsBefore + 1);
    }

    @Test
    void concurrentReviewEvaluationsConvergeOnOneReviewItem() throws Exception {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        // Low mastery on s1: answers are evidence as they are submitted, and nothing evaluates Smart Review until a
        // completion (since X26 a completed free practice re-evaluates it too), so the practice is left open here.
        ActivityAttempt practice = activityAttemptService.startAttempt(learner.childId(), catalog.easy().getId(), null, null);
        answerAll(practice, false);
        assertThat(smartReviewService.findItem(learner.childId(), catalog.s1().getId())).isEmpty();

        List<Optional<ReviewItem>> results = concurrently(8,
                () -> smartReviewService.evaluateAndSchedule(learner.childId(), catalog.s1().getId()));

        assertThat(results).allSatisfy(result -> assertThat(result).isPresent());
        assertThat(results).extracting(result -> result.get().getId()).containsOnly(results.get(0).get().getId());
        assertThat(count("select count(*) from review_items where child_id = ? and skill_id = ?",
                learner.childId(), catalog.s1().getId())).isEqualTo(1);
        assertThat(count("select count(*) from review_schedules s join review_items r on r.id = s.review_item_id "
                + "where r.child_id = ?", learner.childId())).isEqualTo(1);
    }

    @Test
    void concurrentCompletionsOfDifferentAttemptsOnOneSkillStayCoherent() throws Exception {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        LearningPathItem easy = itemFor(learner, catalog.easy());
        LearningPathItem medium = itemFor(learner, catalog.medium());
        ActivityAttempt first = activityAttemptService.startAttempt(learner.childId(), easy.getActivityVersionId(), easy.getId(), null);
        ActivityAttempt second = activityAttemptService.startAttempt(learner.childId(), medium.getActivityVersionId(), medium.getId(), null);
        answerAll(first, false);
        answerAll(second, false);

        List<UUID> attemptIds = List.of(first.getId(), second.getId());
        List<ActivityAttempt> results = concurrently(2, new Callable<>() {
            private final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
            @Override public ActivityAttempt call() {
                return activityAttemptService.completeAttempt(attemptIds.get(next.getAndIncrement()));
            }
        });

        assertThat(results).allSatisfy(result -> assertThat(result.getStatus()).isEqualTo(ActivityAttemptStatus.COMPLETED));
        assertThat(count("select count(*) from adaptation_decisions where child_id = ?", learner.childId())).isEqualTo(2);
        assertThat(count("select count(*) from review_items where child_id = ?", learner.childId())).isEqualTo(1);
        assertThat(pending(learner, LearningPathItemSource.REVIEW)).hasSize(1);
        assertThat(count("select count(*) - count(distinct sequence_order) from learning_path_items where learning_path_id = ?",
                learner.pathId())).isZero();
    }

    // ------------------------------------------------------------------ multi-skill (L7)

    @Test
    void aMultiSkillActivityIsGovernedDeterministicallyByItsWeakestSkill() {
        Catalog catalog = catalog();
        List<Map<String, Object>> contexts = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            Learner learner = enroll(catalog);
            // s1 practised well, s2 poorly, both outside the path.
            ActivityAttempt s1Practice = activityAttemptService.startAttempt(learner.childId(), catalog.medium().getId(), null, null);
            answerAll(s1Practice, true);
            activityAttemptService.completeAttempt(s1Practice.getId());
            ActivityAttempt s2Practice = activityAttemptService.startAttempt(learner.childId(), catalog.s2Only().getId(), null, null);
            answerAll(s2Practice, false);
            activityAttemptService.completeAttempt(s2Practice.getId());

            ActivityAttempt mixed = play(learner, itemFor(learner, catalog.mixed()), true);

            AdaptationDecision decision = adaptationDecisionRepository.findByActivityAttemptId(mixed.getId()).orElseThrow();
            contexts.add(decision.getContext());
            // Review is evaluated per skill: the weak one flagged, the strong one not.
            assertThat(smartReviewService.findItem(learner.childId(), catalog.s2().getId()))
                    .hasValueSatisfying(review -> assertThat(review.getStatus()).isEqualTo(ReviewItemStatus.ACTIVE));
            assertThat(smartReviewService.findItem(learner.childId(), catalog.s1().getId())).isEmpty();
        }
        assertThat(contexts.get(0).get("governingSkillId")).isEqualTo(catalog.s2().getId().toString());
        assertThat(contexts.get(0).get("exercisedSkillIds")).isEqualTo(contexts.get(1).get("exercisedSkillIds"));
        assertThat(contexts.get(1).get("governingSkillId")).isEqualTo(contexts.get(0).get("governingSkillId"));
    }

    // ------------------------------------------------------------------ compatibility reads

    @Test
    void theCompatibilityViewsOnlyRead() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        ActivityAttempt attempt = play(learner, itemFor(learner, catalog.easy()), false);
        Snapshot before = snapshot(learner);

        Optional<PostCompletionService.PlacedAdaptiveStep> step = postCompletionService.findPlacedAdaptiveStep(attempt.getId());
        Optional<PostCompletionService.ReviewState> review = postCompletionService.findReviewState(
                activityAttemptService.getAttempt(attempt.getId()));
        postCompletionService.findPlacedAdaptiveStep(attempt.getId());
        postCompletionService.findReviewState(activityAttemptService.getAttempt(attempt.getId()));

        assertThat(step).hasValueSatisfying(s -> assertThat(s.direction()).isEqualTo("REINFORCEMENT"));
        assertThat(review).hasValueSatisfying(r -> {
            assertThat(r.flaggedForReview()).isTrue();
            assertThat(r.pendingReviewLearningPathItemId()).isNotNull();
        });
        assertThat(snapshot(learner)).isEqualTo(before);
    }

    @Test
    void anAnswerCannotBeAddedAfterCompletion() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        ActivityAttempt attempt = play(learner, itemFor(learner, catalog.easy()), false);
        UUID questionVersionId = activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(catalog.easy().getId())
                .get(0).getQuestionVersionId();

        assertThatThrownBy(() -> activityAttemptService.submitAnswer(attempt.getId(), questionVersionId, "2", 3,
                UUID.randomUUID().toString())).isInstanceOf(IllegalStateException.class);
        assertThat(evidenceCount(learner, catalog.s1())).isEqualTo(2);
    }

    // ------------------------------------------------------------------ fixture

    /**
     * Two skills in one objective. s1: easy, medium, hard single-skill activities.
     * s2: one single-skill activity. mixed: one activity with an s1 and an s2 question.
     */
    private Catalog catalog() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("LOOP_" + suffix, "Loop " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("LOOP_" + suffix, "Loop " + suffix, 90, 99, 99)).getId();

        Skill s1 = skillService.createSkill(subjectId, "LOOP_S1_" + suffix, "Loop s1 " + suffix, null);
        Skill s2 = skillService.createSkill(subjectId, "LOOP_S2_" + suffix, "Loop s2 " + suffix, null);
        LearningObjective objective = learningObjectiveService.create(subjectId, "LOOP_OBJ_" + suffix, "Loop " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), s1.getId());
        learningObjectiveSkillService.link(objective.getId(), s2.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Loop " + suffix);
        var curriculumVersion = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(curriculumVersion.getId(), objective.getId(), 0);

        ActivityVersion easy = activity(subjectId, List.of(s1), 1, question(subjectId, s1, "1 + 1", "2"), question(subjectId, s1, "1 + 2", "3"));
        ActivityVersion medium = activity(subjectId, List.of(s1), 2, question(subjectId, s1, "2 + 2", "4"), question(subjectId, s1, "2 + 3", "5"));
        activity(subjectId, List.of(s1), 3, question(subjectId, s1, "3 + 3", "6"), question(subjectId, s1, "3 + 4", "7"));
        ActivityVersion s2Only = activity(subjectId, List.of(s2), 1, question(subjectId, s2, "5 - 1", "4"), question(subjectId, s2, "5 - 2", "3"));
        ActivityVersion mixed = activity(subjectId, List.of(s1, s2), 5, question(subjectId, s1, "4 + 4", "8"), question(subjectId, s2, "6 - 1", "5"));
        // Published last: a curriculum needs a published activity first (ContentValidator).
        curriculumService.publish(curriculumVersion.getId());
        return new Catalog(curriculum, s1, s2, easy, medium, s2Only, mixed);
    }

    private QuestionVersion question(UUID subjectId, Skill skill, String prompt, String answer) {
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        QuestionVersion draft = questionService.createDraftVersion(question.getId(), prompt + " = ?", answer, null);
        questionService.tagSkill(draft.getId(), skill.getId());
        return questionService.publish(draft.getId());
    }

    private ActivityVersion activity(UUID subjectId, List<Skill> skills, int difficulty, QuestionVersion... questions) {
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Loop activity " + UUID.randomUUID());
        ActivityVersion version = activityService.createDraftVersion(activity.getId(), "Answer", difficulty, 60);
        skills.forEach(skill -> activityService.tagSkill(version.getId(), skill.getId()));
        for (int i = 0; i < questions.length; i++) {
            activityService.addItem(version.getId(), questions[i].getId(), i, BigDecimal.ONE);
        }
        return activityService.publish(version.getId());
    }

    private Learner enroll(Catalog catalog) {
        Family family = familyRepository.save(Family.create("Loop Family"));
        Child child = childRepository.save(Child.enroll(family.getId(), "Loop Child", LocalDate.of(2018, 6, 1)));
        Enrollment enrollment = enrollmentService.enroll(child.getId(), catalog.curriculum().getId());
        return new Learner(child.getId(), learningPathRepository.findByEnrollmentId(enrollment.getId()).orElseThrow().getId());
    }

    private LearningPathItem itemFor(Learner learner, ActivityVersion version) {
        return learningPathService.listItems(learner.pathId()).stream()
                .filter(item -> item.getActivityVersionId().equals(version.getId()))
                .findFirst().orElseThrow();
    }

    /** Start the path item, answer every question, complete - nothing else. */
    private ActivityAttempt play(Learner learner, LearningPathItem item, boolean correct) {
        ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
        answerAll(attempt, correct);
        return activityAttemptService.completeAttempt(attempt.getId());
    }

    private void answerAll(ActivityAttempt attempt, boolean correct) {
        for (ActivityItem item : activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(attempt.getActivityVersionId())) {
            String answer = questionService.getVersion(item.getQuestionVersionId()).getCorrectAnswer();
            activityAttemptService.submitAnswer(attempt.getId(), item.getQuestionVersionId(), correct ? answer : "999", 3,
                    UUID.randomUUID().toString());
        }
    }

    private List<LearningPathItem> pending(Learner learner, LearningPathItemSource source) {
        return learningPathService.listItems(learner.pathId()).stream()
                .filter(item -> item.getSource() == source)
                .filter(item -> item.getStatus() == LearningPathItemStatus.PENDING || item.getStatus() == LearningPathItemStatus.IN_PROGRESS)
                .toList();
    }

    private long evidenceCount(Learner learner, Skill skill) {
        return count("select coalesce(max(evidence_count), 0) from learner_skill_states where child_id = ? and skill_id = ?",
                learner.childId(), skill.getId());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** Row counts of everything a completion may write for this child. */
    private Snapshot snapshot(Learner learner) {
        return new Snapshot(
                count("select count(*) from learning_path_items where learning_path_id = ?", learner.pathId()),
                count("select count(*) from adaptation_decisions where child_id = ?", learner.childId()),
                count("select count(*) from review_items where child_id = ?", learner.childId()),
                count("select count(*) from review_schedules s join review_items r on r.id = s.review_item_id where r.child_id = ?", learner.childId()),
                count("select count(*) from review_attempts a join review_items r on r.id = a.review_item_id where r.child_id = ?", learner.childId()),
                count("select coalesce(sum(evidence_count), 0) from learner_skill_states where child_id = ?", learner.childId()));
    }

    private static <T> List<T> concurrently(int threads, Callable<T> task) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return task.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private record Catalog(Curriculum curriculum, Skill s1, Skill s2, ActivityVersion easy, ActivityVersion medium,
                           ActivityVersion s2Only, ActivityVersion mixed) {
    }

    private record Learner(UUID childId, UUID pathId) {
    }

    private record Snapshot(long pathItems, long decisions, long reviewItems, long reviewSchedules, long reviewAttempts,
                            long evidence) {
    }

}
