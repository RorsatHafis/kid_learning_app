package com.platform.content;

import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
 * X14 over real HTTP against PostgreSQL: what the Studio manages can be renamed, archived and restored.
 * Archived means no new uses - not offered to families, not assigned, not placed on new paths, not
 * chosen for new content - while every child already on it keeps exactly what they had. Refusals say
 * what to do first and change nothing; the catalog is an administrator's, content its owner's.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ContentLifecycleStudioIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Lifecycle-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theCatalogIsRenamedArchivedAndRestoredByAnAdministratorOnly() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String teacher = staff(PlatformRole.TEACHER);
        Catalog c = catalog(admin);

        // Clearer words; the code stays.
        json(put("/api/v1/studio/subjects/" + c.subjectId(), "{\"name\":\"Numbers " + c.suffix() + "\",\"description\":\"Counting and sums\"}",
                admin), 204);
        json(put("/api/v1/studio/skills/" + c.skillId(), "{\"name\":\"Count to ten\",\"description\":\"Counts up to ten objects.\"}", admin), 204);
        json(put("/api/v1/studio/objectives/" + c.objectiveId(), "{\"name\":\"Counting objects\"}", admin), 204);
        JsonNode catalog = json(get("/api/v1/studio/subjects/" + c.subjectId() + "/catalog", admin), 200);
        assertThat(catalog.get("subject").get("name").asString()).isEqualTo("Numbers " + c.suffix());
        assertThat(catalog.get("skills").get(0).get("name").asString()).isEqualTo("Count to ten");
        assertThat(catalog.get("skills").get(0).get("code").asString()).startsWith("COUNT_");
        assertThat(catalog.get("objectives").get(0).get("title").asString()).isEqualTo("Counting objects");

        // A teacher can't redefine the catalog.
        assertThat(put("/api/v1/studio/skills/" + c.skillId(), "{\"name\":\"Mine\"}", teacher).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/studio/skills/" + c.skillId() + "/archive", "", teacher).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select name from skills where id = ?::uuid", String.class, c.skillId())).isEqualTo("Count to ten");

        // Archived: out of the catalog and of new content; restored: back.
        json(post("/api/v1/studio/skills/" + c.skillId() + "/archive", "", admin), 204);
        assertThat(json(get("/api/v1/studio/subjects/" + c.subjectId() + "/catalog", admin), 200).get("skills")).isEmpty();
        assertThat(json(get("/api/v1/studio/subjects/" + c.subjectId() + "/catalog?archived=true", admin), 200).get("skills").get(0)
                .get("status").asString()).isEqualTo("ARCHIVED");
        assertThat(post("/api/v1/studio/questions", numeric(c.subjectId(), "1 + 1 = ?", "2", c.skillId()), admin).statusCode()).isIn(400, 409);
        assertThat(post("/api/v1/studio/skills/" + c.skillId() + "/archive", "", admin).statusCode()).isEqualTo(409);
        json(post("/api/v1/studio/skills/" + c.skillId() + "/restore", "", admin), 204);
        json(post("/api/v1/studio/questions", numeric(c.subjectId(), "1 + 1 = ?", "2", c.skillId()), admin), 201);

        json(post("/api/v1/studio/objectives/" + c.objectiveId() + "/archive", "", admin), 204);
        assertThat(json(get("/api/v1/studio/subjects/" + c.subjectId() + "/catalog?archived=true", admin), 200).get("objectives").get(0)
                .get("archived").asBoolean()).isTrue();
        json(post("/api/v1/studio/objectives/" + c.objectiveId() + "/restore", "", admin), 204);

        // A subject with nothing offered can go; its archived list holds it.
        json(post("/api/v1/studio/subjects/" + c.subjectId() + "/archive", "", admin), 204);
        assertThat(ids(json(get("/api/v1/studio/subjects", admin), 200))).doesNotContain(c.subjectId());
        assertThat(ids(json(get("/api/v1/studio/subjects?archived=true", admin), 200))).contains(c.subjectId());
        json(post("/api/v1/studio/subjects/" + c.subjectId() + "/restore", "", admin), 204);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where resource_id = ?::uuid and action in "
                + "('CONTENT_ARCHIVED','CONTENT_RESTORED','CONTENT_UPDATED')", Integer.class, c.skillId())).isEqualTo(3);
    }

    @Test
    void archivedContentIsNotUsedAnewWhileChildrenAlreadyOnItKeepEverything() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        Catalog c = catalog(admin);
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + c.subjectId() + "\",\"title\":\"Counting\"}", admin), 201)
                .get("id").asString();
        String first = publishedActivity(admin, c, lessonId, "Count the stars " + c.suffix(), "3 + 0 = ?", "3");
        String second = publishedActivity(admin, c, lessonId, "Count the moons " + c.suffix(), "2 + 2 = ?", "4");
        String curriculumId = publishedCurriculum(admin, c);

        // A child starts the path: both activities are on it.
        String parent = registerParent();
        String early = createChild(parent, "Early");
        String earlyEnrollment = enroll(parent, early, curriculumId);
        assertThat(stepTitles(parent, earlyEnrollment)).containsExactlyInAnyOrder("Count the stars " + c.suffix(), "Count the moons " + c.suffix());

        // Renamed and archived: the activity is no longer placed on new paths...
        json(put("/api/v1/studio/activities/" + first, "{\"name\":\"Count the bright stars " + c.suffix() + "\"}", admin), 204);
        json(post("/api/v1/studio/activities/" + first + "/archive", "", admin), 204);
        String late = createChild(parent, "Late");
        assertThat(stepTitles(parent, enroll(parent, late, curriculumId))).containsExactly("Count the moons " + c.suffix());
        // ...while the child already on it keeps it, renamed.
        assertThat(stepTitles(parent, earlyEnrollment)).contains("Count the bright stars " + c.suffix());

        // The Studio's views: active and archived.
        JsonNode active = json(get("/api/v1/studio/lessons?subjectId=" + c.subjectId(), admin), 200);
        assertThat(titles(active.get("items").get(0).get("activities"))).containsExactly("Count the moons " + c.suffix());
        JsonNode archived = json(get("/api/v1/studio/lessons?subjectId=" + c.subjectId() + "&archived=true", admin), 200);
        assertThat(titles(archived.get("items").get(0).get("activities"))).containsExactly("Count the bright stars " + c.suffix());

        // A lesson goes only when its activities have; an activity comes back only with its lesson.
        assertThat(post("/api/v1/studio/lessons/" + lessonId + "/archive", "", admin).statusCode()).isEqualTo(409);
        json(post("/api/v1/studio/activities/" + second + "/archive", "", admin), 204);
        json(post("/api/v1/studio/lessons/" + lessonId + "/archive", "", admin), 204);
        HttpResponse<String> refused = post("/api/v1/studio/activities/" + first + "/restore", "", admin);
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).contains("Restore the lesson first");
        json(post("/api/v1/studio/lessons/" + lessonId + "/restore", "", admin), 204);
        json(post("/api/v1/studio/activities/" + first + "/restore", "", admin), 204);
        json(post("/api/v1/studio/activities/" + second + "/restore", "", admin), 204);

        // An archived curriculum: no longer offered or startable, the subject can't go while it is offered, and children on it keep it.
        HttpResponse<String> subjectRefused = post("/api/v1/studio/subjects/" + c.subjectId() + "/archive", "", admin);
        assertThat(subjectRefused.statusCode()).isEqualTo(409);
        assertThat(subjectRefused.body()).contains("Archive those curricula first");
        json(post("/api/v1/studio/curricula/" + curriculumId + "/archive", "", admin), 204);
        assertThat(get("/api/v1/curricula", parent).body()).doesNotContain(curriculumId);
        String another = createChild(parent, "Another");
        int enrollments = jdbc.queryForObject("select count(*) from enrollments where child_id = ?::uuid", Integer.class, another);
        assertThat(post("/api/v1/children/" + another + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}", parent).statusCode())
                .isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from enrollments where child_id = ?::uuid", Integer.class, another)).isEqualTo(enrollments);
        assertThat(stepTitles(parent, earlyEnrollment)).hasSize(2);
        assertThat(ids(json(get("/api/v1/studio/curricula?archived=true", admin), 200))).contains(curriculumId);
        json(post("/api/v1/studio/curricula/" + curriculumId + "/restore", "", admin), 204);
        assertThat(get("/api/v1/curricula", parent).body()).contains(curriculumId);

        // Questions: archived ones leave the bank and come back.
        String questionId = json(post("/api/v1/studio/questions", numeric(c.subjectId(), "9 - 1 = ?", "8", c.skillId()), admin), 201)
                .get("id").asString();
        json(post("/api/v1/studio/questions/" + questionId + "/archive", "", admin), 204);
        assertThat(ids(json(get("/api/v1/studio/questions?subjectId=" + c.subjectId(), admin), 200).get("items"))).doesNotContain(questionId);
        assertThat(ids(json(get("/api/v1/studio/questions?subjectId=" + c.subjectId() + "&archived=true", admin), 200).get("items")))
                .containsExactly(questionId);
        // Another author can't touch it.
        assertThat(post("/api/v1/studio/questions/" + questionId + "/restore", "", staff(PlatformRole.TEACHER)).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ content

    private record Catalog(String suffix, String subjectId, String ageHubId, String skillId, String objectiveId) {
    }

    private Catalog catalog(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"LIFE_" + suffix + "\",\"name\":\"Lifecycle " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"COUNT_" + suffix
                + "\",\"name\":\"Count\",\"description\":\"Counts.\"}", admin), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"OBJ_" + suffix
                + "\",\"title\":\"Counting\"}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("LIFE_" + suffix, "Lifecycle " + suffix, 90, 99, 99)).getId().toString();
        return new Catalog(suffix, subjectId, ageHubId, skillId, objectiveId);
    }

    private static String numeric(String subjectId, String prompt, String answer, String skillId) {
        return "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"" + prompt + "\",\"correctAnswer\":\"" + answer
                + "\",\"difficultyLevel\":1,\"skillIds\":[\"" + skillId + "\"]}";
    }

    private String publishedActivity(String admin, Catalog c, String lessonId, String title, String prompt, String answer) throws Exception {
        String questionVersion = json(post("/api/v1/studio/questions", numeric(c.subjectId(), prompt, answer, c.skillId()), admin), 201)
                .get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/question-versions/" + questionVersion + "/publish", "", admin), 200);
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities", "{\"title\":\"" + title + "\",\"skillIds\":[\""
                + c.skillId() + "\"]}", admin), 201);
        String versionId = activity.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + versionId + "/items", "{\"items\":[{\"questionVersionId\":\"" + questionVersion + "\"}]}",
                admin), 200);
        json(post("/api/v1/studio/activity-versions/" + versionId + "/publish", "", admin), 200);
        return activity.get("id").asString();
    }

    private String publishedCurriculum(String admin, Catalog c) throws Exception {
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + c.subjectId() + "\",\"ageHubId\":\"" + c.ageHubId()
                + "\",\"name\":\"Lifecycle " + c.suffix() + "\"}", admin), 201);
        String versionId = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + versionId + "/objectives", "{\"objectiveIds\":[\"" + c.objectiveId() + "\"]}", admin), 200);
        json(post("/api/v1/studio/curriculum-versions/" + versionId + "/publish", "", admin), 200);
        return curriculum.get("id").asString();
    }

    private String createChild(String parent, String name) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}", parent),
                201).get("id").asString();
    }

    private String enroll(String parent, String childId, String curriculumId) throws Exception {
        return json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}", parent), 201)
                .get("id").asString();
    }

    private List<String> stepTitles(String parent, String enrollmentId) throws Exception {
        List<String> titles = new ArrayList<>();
        json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent), 200).get("steps").forEach(s -> titles.add(s.get("title").asString()));
        return titles;
    }

    private static List<String> titles(JsonNode items) {
        List<String> titles = new ArrayList<>();
        items.forEach(item -> titles.add(item.get("title").asString()));
        return titles;
    }

    private static List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asString()));
        return ids;
    }

    // ------------------------------------------------------------------ accounts and HTTP

    private String staff(PlatformRole role) throws Exception {
        String email = "lifecycle-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"lifecycle-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response.body().isEmpty() ? null : JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)),
                bearer);
    }

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)),
                bearer);
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
