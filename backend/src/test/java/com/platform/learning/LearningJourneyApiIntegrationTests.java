package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.content.entity.Activity;
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
import com.platform.knowledge.service.SkillPrerequisiteService;
import com.platform.knowledge.service.SkillService;
import com.platform.learner.service.EvidenceStandard.EvidenceLevel;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.repository.LearningPathRepository;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.EnrollmentService;
import com.platform.learning.service.LearningJourneyService;
import com.platform.learning.service.LearningJourneyService.JourneyStep;
import com.platform.learning.service.LearningJourneyService.PlacementReason;
import com.platform.learning.service.LearningPathService;
import com.platform.learning.service.SkillProgressService;
import com.platform.learning.service.SkillProgressService.ObjectiveStanding;
import com.platform.learning.service.SkillProgressService.SkillProgress;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The child/parent learning read APIs over real HTTP against PostgreSQL: a child's
 * enrollments are discoverable from the server (no client-side pointer), the journey
 * explains each step from recorded decisions, answer feedback carries the correct
 * answer only for a recorded answer, skills report evidence strength separately from
 * performance, and every view is family-scoped.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class LearningJourneyApiIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}") private int port;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private ChildRepository childRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private CurriculumService curriculumService;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private SkillService skillService;
    @Autowired private SkillPrerequisiteService skillPrerequisiteService;
    @Autowired private QuestionService questionService;
    @Autowired private ActivityService activityService;
    @Autowired private ActivityItemRepository activityItemRepository;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private LearningPathRepository learningPathRepository;
    @Autowired private LearningPathService learningPathService;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private LearningJourneyService learningJourneyService;
    @Autowired private SkillProgressService skillProgressService;

    private final HttpClient http = HttpClient.newHttpClient();

    // ------------------------------------------------------------------ over HTTP, seeded catalog

    @Test
    void aNewChildsLearningIsDiscoverableAndExplainedFromTheServer() throws Exception {
        String token = registerParent();
        String childId = createChild(token);

        // Auto-enrolled with the child (Battle 2B): the server now says so.
        JsonNode enrollments = json(get("/api/v1/children/" + childId + "/enrollments", token), 200);
        assertThat(enrollments).hasSize(1);
        JsonNode enrollment = enrollments.get(0);
        assertThat(enrollment.get("curriculum").get("subjectCode").asString()).isEqualTo("MATHS");
        assertThat(enrollment.get("completedSteps").asInt()).isZero();
        JsonNode next = enrollment.get("nextStep");
        assertThat(next.get("title").asString()).isEqualTo("Addition Recognition Practice");
        assertThat(next.get("explanation").get("reason").asString()).isEqualTo("CURRICULUM");

        String enrollmentId = enrollment.get("enrollmentId").asString();
        JsonNode journey = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", token), 200);
        assertThat(journey.get("totalSteps").asInt()).isEqualTo(journey.get("steps").size()).isEqualTo(6);
        assertThat(journey.get("nextStepId").asString()).isEqualTo(next.get("learningPathItemId").asString());

        // Play the first step wrong, over HTTP, reading feedback from each answer.
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts",
                "{\"activityVersionId\":\"" + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                        + next.get("learningPathItemId").asString() + "\"}", token), 201).get("id").asString();
        JsonNode content = json(get("/api/v1/activity-versions/" + next.get("activityVersionId").asString() + "?childId=" + childId, token), 200);
        List<String> correctAnswers = new ArrayList<>();
        for (JsonNode item : content.get("items")) {
            JsonNode answer = json(post("/api/v1/activity-attempts/" + attemptId + "/answers",
                    "{\"questionVersionId\":\"" + item.get("questionVersionId").asString()
                            + "\",\"submittedAnswer\":\"999\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", token), 201);
            assertThat(answer.get("correct").asBoolean()).isFalse();
            correctAnswers.add(answer.get("correctAnswer").get("text").asString());
        }
        assertThat(correctAnswers).containsExactly("2", "3");
        // The answer key is feedback on a recorded answer, never part of the content.
        assertThat(content.toString()).doesNotContain("correct");
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", token), 200);

        JsonNode history = json(get("/api/v1/children/" + childId + "/activity-history", token), 200);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("correctAnswers").asInt()).isZero();
        assertThat(history.get(0).get("totalAnswers").asInt()).isEqualTo(2);
        assertThat(history.get(0).get("decision").get("type").asString()).isEqualTo("REMEDIATE");
        assertThat(history.get(0).get("decision").get("governingSkill").get("name").asString()).isEqualTo("Recognize addition");

        // Smart Review flagged the skill and placed a review, explained by that skill.
        JsonNode after = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", token), 200);
        assertThat(after.get("completedSteps").asInt()).isEqualTo(1);
        JsonNode review = stream(after.get("steps")).stream()
                .filter(step -> step.get("source").asString().equals("REVIEW")).findFirst().orElseThrow();
        assertThat(review.get("explanation").get("reason").asString()).isEqualTo("REVIEW");
        assertThat(review.get("explanation").get("skill").get("name").asString()).isEqualTo("Recognize addition");

        JsonNode skills = json(get("/api/v1/children/" + childId + "/skills", token), 200);
        JsonNode objective = skills.get(0).get("objectives").get(0);
        assertThat(objective.get("standing").asString()).isEqualTo("IN_PROGRESS");
        JsonNode recognition = stream(objective.get("skills")).stream()
                .filter(skill -> skill.get("name").asString().equals("Recognize addition")).findFirst().orElseThrow();
        // Two wrong answers are partial evidence: too early to call it weak.
        assertThat(recognition.get("evidenceLevel").asString()).isEqualTo("PARTIAL");
        assertThat(recognition.get("standing").asString()).isEqualTo("TOO_EARLY_TO_TELL");
        assertThat(recognition.get("scoredAnswers").asInt()).isEqualTo(2);
        assertThat(recognition.get("inReview").asBoolean()).isTrue();
        assertThat(recognition.has("masteryProbability")).isFalse();
        JsonNode untouched = stream(objective.get("skills")).stream()
                .filter(skill -> skill.get("name").asString().equals("Add within 10")).findFirst().orElseThrow();
        assertThat(untouched.get("standing").asString()).isEqualTo("NOT_STARTED");
    }

    @Test
    void everyLearningViewIsFamilyScoped() throws Exception {
        String owner = registerParent();
        String childId = createChild(owner);
        String enrollmentId = json(get("/api/v1/children/" + childId + "/enrollments", owner), 200)
                .get(0).get("enrollmentId").asString();
        String stranger = registerParent();

        for (String path : List.of("/api/v1/children/" + childId, "/api/v1/children/" + childId + "/enrollments",
                "/api/v1/enrollments/" + enrollmentId + "/journey", "/api/v1/children/" + childId + "/skills",
                "/api/v1/children/" + childId + "/activity-history")) {
            // Another family's child is answered like one that doesn't exist (no existence oracle).
            assertThat(get(path, stranger).statusCode()).as(path).isEqualTo(404);
            assertThat(get(path, null).statusCode()).as(path).isEqualTo(401);
            assertThat(get(path, owner).statusCode()).as(path).isEqualTo(200);
        }
        assertThat(patch("/api/v1/children/" + childId, "{\"displayName\":\"Taken\"}", stranger).statusCode()).isEqualTo(404);
    }

    @Test
    void aParentCanRenameTheirChild() throws Exception {
        String token = registerParent();
        String childId = createChild(token);

        assertThat(json(patch("/api/v1/children/" + childId, "{\"displayName\":\"  Sokha  \"}", token), 200)
                .get("displayName").asString()).isEqualTo("Sokha");
        assertThat(json(get("/api/v1/children/" + childId, token), 200).get("displayName").asString()).isEqualTo("Sokha");
        assertThat(patch("/api/v1/children/" + childId, "{\"displayName\":\"  \"}", token).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/children/" + childId + "/activity-history?limit=0", token).statusCode()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ service level, own catalog

    @Test
    void anAdaptiveStepIsExplainedByTheDecisionThatPlacedIt() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);

        // Fail the first activity on the skill: REMEDIATE onto its prerequisite.
        LearningPathItem first = learningPathService.getNextItem(learner.pathId());
        play(learner, first, false);

        JourneyStep adaptive = learningJourneyService.journeyFor(learner.enrollmentId()).steps().stream()
                .filter(step -> step.source() == LearningPathItemSource.ADAPTIVE).findFirst().orElseThrow();
        assertThat(adaptive.activityVersionId()).isEqualTo(catalog.prerequisiteActivity().getId());
        assertThat(adaptive.explanation().reason()).isEqualTo(PlacementReason.REINFORCE_PREREQUISITE);
        assertThat(adaptive.explanation().skill().id()).isEqualTo(catalog.skill().getId());
        assertThat(adaptive.explanation().targetSkill().id()).isEqualTo(catalog.prerequisite().getId());
        assertThat(adaptive.explanation().afterActivityTitle()).isEqualTo(catalog.firstTitle());
        assertThat(adaptive.explanation().afterScore()).isEqualByComparingTo("0");
    }

    @Test
    void practicePlacedAfterAStruggleComesBeforeTheRestOfTheCurriculum() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);

        LearningPathItem first = learningPathService.getNextItem(learner.pathId());
        play(learner, first, false);

        // LI-1: the reinforcement is served now, not after every remaining curriculum step.
        LearningPathItem next = learningPathService.getNextItem(learner.pathId());
        assertThat(next.getSource()).isEqualTo(LearningPathItemSource.ADAPTIVE);
        assertThat(next.getActivityVersionId()).isEqualTo(catalog.prerequisiteActivity().getId());

        var journey = learningJourneyService.journeyFor(learner.enrollmentId());
        assertThat(journey.nextStepId()).isEqualTo(next.getId());
        // Smart Review also scheduled the struggling skill; both are served, in the order they were
        // placed, before the curriculum's next step.
        assertThat(journey.steps()).extracting(JourneyStep::source).containsExactly(
                LearningPathItemSource.CURRICULUM, LearningPathItemSource.ADAPTIVE, LearningPathItemSource.REVIEW,
                LearningPathItemSource.CURRICULUM);

        play(learner, next, true);
        assertThat(learningPathService.getNextItem(learner.pathId()).getSource()).isEqualTo(LearningPathItemSource.REVIEW);
    }

    @Test
    void aJourneyIsToldInTheChaptersItsEducatorsOrdered() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);

        var before = learningJourneyService.journeyFor(learner.enrollmentId());
        assertThat(before.chapters()).hasSize(1);
        var chapter = before.chapters().get(0);
        assertThat(chapter.position()).isEqualTo(1);
        assertThat(chapter.totalSteps()).isEqualTo(2);
        assertThat(chapter.completedSteps()).isZero();
        assertThat(chapter.state()).isEqualTo(LearningJourneyService.ChapterState.CURRENT);
        assertThat(before.steps()).allSatisfy(step -> assertThat(step.chapterId()).isEqualTo(chapter.id()));

        play(learner, learningPathService.getNextItem(learner.pathId()), false);

        // The prerequisite practised is covered by no chapter of this curriculum: it is told as
        // extra practice (no chapter) and the chapter keeps its two steps.
        var after = learningJourneyService.journeyFor(learner.enrollmentId());
        JourneyStep extra = after.steps().stream().filter(step -> step.source() == LearningPathItemSource.ADAPTIVE)
                .findFirst().orElseThrow();
        assertThat(extra.chapterId()).isNull();
        assertThat(after.chapters().get(0).completedSteps()).isEqualTo(1);
        assertThat(after.chapters().get(0).state()).isEqualTo(LearningJourneyService.ChapterState.STARTED);
    }

    @Test
    void skillStandingsFollowTheEvidenceNotTheCompletionCount() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);

        play(learner, learningPathService.getNextItem(learner.pathId()), true);
        SkillProgress afterThree = skill(learner, catalog.skill());
        assertThat(afterThree.evidenceLevel()).isEqualTo(EvidenceLevel.SUFFICIENT);
        assertThat(afterThree.standing()).isIn(SkillStanding.PROGRESSING, SkillStanding.SECURE);
        assertThat(afterThree.correctAnswers()).isEqualTo(3);

        // The prerequisite is mapped to no objective of this curriculum; the objective's
        // own skill is sufficiently evidenced but one objective skill alone decides it.
        assertThat(skillProgressService.progressFor(learner.childId()).get(0).objectives().get(0).standing())
                .isIn(ObjectiveStanding.IN_PROGRESS, ObjectiveStanding.SECURE);
    }

    @Test
    void historyIsReadAPageAtATimeFromWhereTheLastPageEnded() {
        Catalog catalog = catalog();
        Learner learner = enroll(catalog);
        play(learner, learningPathService.getNextItem(learner.pathId()), false);
        play(learner, learningPathService.getNextItem(learner.pathId()), true);
        play(learner, learningPathService.getNextItem(learner.pathId()), true);

        var all = learningJourneyService.completedActivities(learner.childId(), 10);
        assertThat(all).hasSize(3);
        assertThat(all).isSortedAccordingTo(java.util.Comparator.comparing(
                LearningJourneyService.CompletedActivity::completedAt).reversed());

        var first = learningJourneyService.completedActivities(learner.childId(), 2);
        var last = first.get(first.size() - 1);
        var earlier = learningJourneyService.completedActivities(learner.childId(), 2, last.completedAt(), last.attemptId());
        // Newest first, no repeats and nothing skipped across the two pages.
        assertThat(java.util.stream.Stream.concat(first.stream(), earlier.stream())
                .map(LearningJourneyService.CompletedActivity::attemptId).toList())
                .containsExactlyElementsOf(all.stream().map(LearningJourneyService.CompletedActivity::attemptId).toList());
        assertThat(learningJourneyService.completedActivities(learner.childId(), 2,
                earlier.get(0).completedAt(), earlier.get(0).attemptId())).isEmpty();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                learningJourneyService.completedActivities(learner.childId(), 2, last.completedAt(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ fixture

    /** Skill with a prerequisite; the curriculum's objective covers the skill; activities of three questions each. */
    private Catalog catalog() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("JOURNEY_" + suffix, "Journey " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("JOURNEY_" + suffix, "Journey " + suffix, 90, 99, 99)).getId();
        Skill prerequisite = skillService.createSkill(subjectId, "JOURNEY_PRE_" + suffix, "Prerequisite " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "JOURNEY_" + suffix, "Skill " + suffix, null);
        skillPrerequisiteService.addPrerequisite(skill.getId(), prerequisite.getId());
        LearningObjective objective = learningObjectiveService.create(subjectId, "JOURNEY_OBJ_" + suffix, "Journey " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Journey " + suffix);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);

        String firstTitle = "Journey first " + suffix;
        activity(subjectId, skill, firstTitle, 2);
        activity(subjectId, skill, "Journey second " + suffix, 4);
        ActivityVersion prerequisiteActivity = activity(subjectId, prerequisite, "Journey prerequisite " + suffix, 1);
        // Published last: a curriculum needs a published activity first (ContentValidator).
        curriculumService.publish(version.getId());
        return new Catalog(curriculum, skill, prerequisite, firstTitle, prerequisiteActivity);
    }

    private ActivityVersion activity(UUID subjectId, Skill skill, String title, int difficulty) {
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", title);
        ActivityVersion version = activityService.createDraftVersion(activity.getId(), "Add", difficulty, 60);
        activityService.tagSkill(version.getId(), skill.getId());
        for (int i = 0; i < 3; i++) {
            Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
            QuestionVersion draft = questionService.createDraftVersion(question.getId(), i + " + 2 = ?", String.valueOf(i + 2), null);
            questionService.tagSkill(draft.getId(), skill.getId());
            QuestionVersion questionVersion = questionService.publish(draft.getId());
            activityService.addItem(version.getId(), questionVersion.getId(), i, BigDecimal.ONE);
        }
        return activityService.publish(version.getId());
    }

    private Learner enroll(Catalog catalog) {
        Family family = familyRepository.save(Family.create("Journey Family"));
        Child child = childRepository.save(Child.enroll(family.getId(), "Journey Child", LocalDate.of(2018, 6, 1)));
        Enrollment enrollment = enrollmentService.enrollInPlatformCurriculum(child.getId(), catalog.curriculum().getId());
        return new Learner(child.getId(), enrollment.getId(),
                learningPathRepository.findByEnrollmentId(enrollment.getId()).orElseThrow().getId());
    }

    private void play(Learner learner, LearningPathItem item, boolean correct) {
        ActivityAttempt attempt = activityAttemptService.startAttempt(learner.childId(), item.getActivityVersionId(), item.getId(), null);
        for (var activityItem : activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(item.getActivityVersionId())) {
            String answer = correct ? questionService.getVersion(activityItem.getQuestionVersionId()).getCorrectAnswer() : "999";
            activityAttemptService.submitAnswer(attempt.getId(), activityItem.getQuestionVersionId(), answer, 3,
                    UUID.randomUUID().toString());
        }
        activityAttemptService.completeAttempt(attempt.getId());
    }

    private SkillProgress skill(Learner learner, Skill skill) {
        return skillProgressService.progressFor(learner.childId()).get(0).objectives().stream()
                .flatMap(objective -> objective.skills().stream())
                .filter(progress -> progress.skillId().equals(skill.getId()))
                .findFirst().orElseThrow();
    }

    private record Catalog(Curriculum curriculum, Skill skill, Skill prerequisite, String firstTitle,
                           ActivityVersion prerequisiteActivity) {
    }

    private record Learner(UUID childId, UUID enrollmentId, UUID pathId) {
    }

    // ------------------------------------------------------------------ HTTP

    private String registerParent() throws Exception {
        HttpResponse<String> res = post("/api/v1/auth/register",
                "{\"email\":\"journey-" + UUID.randomUUID() + "@example.com\",\"password\":\"Journey-Test-Pass-2026\","
                        + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null);
        return json(res, 201).get("accessToken").asString();
    }

    private String createChild(String token) throws Exception {
        String dateOfBirth = LocalDate.now().minusYears(6).toString();
        return json(post("/api/v1/children", "{\"displayName\":\"Journey Child\",\"dateOfBirth\":\"" + dateOfBirth + "\"}",
                token), 201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private static List<JsonNode> stream(JsonNode array) {
        List<JsonNode> nodes = new ArrayList<>();
        array.forEach(nodes::add);
        return nodes;
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> patch(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

}
