package com.platform.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.SkillService;
import com.platform.learner.service.LearningDays;
import com.platform.learner.service.MasteryEngine;
import com.platform.review.entity.ReviewItem;
import com.platform.review.service.SmartReviewService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * D-18 (owner decision 2026-11-11): Smart Review revisits every skill the evidence doesn't show secure yet, with spaced
 * review - a failing skill at once (as before), any other not-yet-secure one (including secure-level answers from one
 * day only, D-17) on the child's next learning day - and lets it go only once it is secure. Too little evidence to say
 * anything brings nothing.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class NotYetSecureReviewIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private SkillService skillService;
    @Autowired private MasteryEngine masteryEngine;
    @Autowired private SmartReviewService smartReview;
    @Autowired private LearningDays learningDays;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aSkillRightInOneSittingIsRevisitedOnTheNextLearningDayAndLetGoOnceSecure() throws Exception {
        UUID child = child();
        UUID skill = skill();
        for (int i = 0; i < 3; i++) masteryEngine.recordEvidence(child, skill, true, UUID.randomUUID());

        // Secure-level answers from one day only: "progressing", so it comes back - not now, on the next learning day.
        Optional<ReviewItem> item = smartReview.evaluateAndSchedule(child, skill);
        assertThat(item).isPresent();
        Instant due = dueAt(item.get());
        Instant tomorrow = LearningDays.dayOf(Instant.now(), learningDays.zoneOf(child)).plusDays(1)
                .atStartOfDay(learningDays.zoneOf(child)).toInstant();
        assertThat(due).isEqualTo(tomorrow);

        // Another learning day's evidence: secure, so it is let go.
        jdbc.update("insert into learner_skill_history (id, child_id, skill_id, mastery_probability, confidence, triggered_by_id,"
                        + " triggered_by_type, occurred_at) values (?, ?, ?, 1, 0.3, ?, 'ACTIVITY_ATTEMPT', ?)",
                UUID.randomUUID(), child, skill, UUID.randomUUID(), Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)));
        assertThat(smartReview.evaluateAndSchedule(child, skill)).isEmpty();
        assertThat(jdbc.queryForObject("select status from review_items where child_id = ? and skill_id = ?", String.class, child, skill))
                .isEqualTo("RETIRED");
    }

    @Test
    void aFailingSkillIsDueAtOnceAndTooLittleEvidenceBringsNothing() throws Exception {
        UUID child = child();
        UUID failing = skill();
        masteryEngine.recordEvidence(child, failing, false, UUID.randomUUID());
        masteryEngine.recordEvidence(child, failing, false, UUID.randomUUID());
        Optional<ReviewItem> item = smartReview.evaluateAndSchedule(child, failing);
        assertThat(item).isPresent();
        assertThat(dueAt(item.get())).isBeforeOrEqualTo(Instant.now());

        // One answer: too early to tell - no review.
        UUID early = skill();
        masteryEngine.recordEvidence(child, early, true, UUID.randomUUID());
        assertThat(smartReview.evaluateAndSchedule(child, early)).isEmpty();

        // Getting better but not secure (0.5 to the threshold, enough answers): still revisited, spaced.
        UUID middling = skill();
        masteryEngine.recordEvidence(child, middling, true, UUID.randomUUID());
        masteryEngine.recordEvidence(child, middling, false, UUID.randomUUID());
        masteryEngine.recordEvidence(child, middling, true, UUID.randomUUID());
        assertThat(smartReview.evaluateAndSchedule(child, middling)).isPresent();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private Instant dueAt(ReviewItem item) {
        return jdbc.queryForObject("select due_at from review_schedules where review_item_id = ?", Timestamp.class, item.getId())
                .toInstant();
    }

    private UUID skill() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("REV_" + suffix, "Review " + suffix, null)).getId();
        Skill skill = skillService.createSkill(subjectId, "REV_S_" + suffix, "Skill " + suffix, null);
        return skill.getId();
    }

    private UUID child() throws Exception {
        String parent = JSON.readTree(post("/api/v1/auth/register", "{\"email\":\"review-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"Review-Test-Pass-2026\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null).body())
                .get("accessToken").asString();
        return UUID.fromString(JSON.readTree(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\",\"familyChoosesSubjects\":true}", parent).body()).get("id").asString());
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
