package com.platform.content;

import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LOCALIZATION.md "Content in two languages", slice 3 (V81): a subject's, skill's, chapter's and curriculum's Khmer
 * name is saved beside the English, kept or replaced on edit, removed when cleared, and given to a Khmer reader on
 * the family catalog, the child's path and the skills page - per name, the English wherever no Khmer is written.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Tag("integration")
class BilingualCatalogNamesIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Catalog-Names-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void catalogNamesAreSavedInBothLanguagesAndReadInTheReadersLanguage() throws Exception {
        String admin = staff();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // English and Khmer saved together, on create.
        JsonNode subject = json(post("/api/v1/studio/subjects", "{\"code\":\"KMN_" + suffix + "\",\"name\":\"Numbers " + suffix
                + "\",\"khmer\":{\"name\":\"លេខ " + suffix + "\"}}", admin, null), 201);
        String subjectId = subject.get("id").asString();
        assertThat(subject.get("khmer").get("name").asString()).isEqualTo("លេខ " + suffix);
        JsonNode skill = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"CNT_" + suffix
                + "\",\"name\":\"Counting\",\"description\":\"Counting to ten.\",\"khmer\":{\"name\":\"ការរាប់\","
                + "\"description\":\"រាប់ដល់ដប់។\"}}", admin, null), 201);
        String skillId = skill.get("id").asString();
        assertThat(skill.get("khmer").get("name").asString()).isEqualTo("ការរាប់");
        assertThat(skill.get("khmer").get("description").asString()).isEqualTo("រាប់ដល់ដប់។");
        // A chapter with no Khmer yet: it is shown in English, and gets its Khmer on an edit.
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"CH_" + suffix
                + "\",\"title\":\"Counting things\",\"description\":\"Count what you see.\"}", admin, null), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin, null), 200);

        // The chapter, read on the Studio's catalog: no Khmer.
        JsonNode objective = json(get("/api/v1/studio/subjects/" + subjectId + "/catalog", admin, null), 200).get("objectives").get(0);
        assertThat(objective.get("khmer").get("name").isNull()).isTrue();

        // A published activity on the chapter's skill, and a curriculum with its Khmer name.
        String questionVersionId = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"How many?\",\"difficultyLevel\":1,"
                + "\"options\":[{\"label\":\"three\",\"correct\":true},{\"label\":\"two\",\"correct\":false}],\"skillIds\":[\"" + skillId
                + "\"]}", admin, null), 201).get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/question-versions/" + questionVersionId + "/publish", "", admin, null), 200);
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Count\"}", admin, null), 201)
                .get("id").asString();
        String activityVersionId = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Count the stars\",\"skillIds\":[\"" + skillId + "\"]}", admin, null), 201)
                .get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + questionVersionId + "\"}]}", admin, null), 200);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin, null), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("KMN_" + suffix, "Names " + suffix, 90, 99, 99)).getId().toString();
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"ageHubId\":\"" + ageHubId
                + "\",\"name\":\"First numbers " + suffix + "\",\"khmer\":{\"name\":\"លេខដំបូង " + suffix + "\"}}", admin, null), 201);
        String curriculumId = curriculum.get("id").asString();
        assertThat(curriculum.get("khmer").get("name").asString()).isEqualTo("លេខដំបូង " + suffix);
        String curriculumVersion = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/objectives", "{\"objectiveIds\":[\"" + objectiveId + "\"]}",
                admin, null), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/publish", "", admin, null), 200);

        // Edits: the chapter gets its Khmer; the skill's English changes and its Khmer, absent from the request, stays.
        status(put("/api/v1/studio/objectives/" + objectiveId, "{\"name\":\"Counting things\",\"description\":\"Count what you see.\","
                + "\"khmer\":{\"name\":\"រាប់វត្ថុ\",\"description\":\"រាប់អ្វីដែលអ្នកឃើញ។\"}}", admin, null), 204);
        status(put("/api/v1/studio/skills/" + skillId, "{\"name\":\"Counting objects\",\"description\":\"Counting to ten.\"}",
                admin, null), 204);
        JsonNode catalog = json(get("/api/v1/studio/subjects/" + subjectId + "/catalog", admin, null), 200);
        assertThat(catalog.get("skills").get(0).get("name").asString()).isEqualTo("Counting objects");
        assertThat(catalog.get("skills").get(0).get("khmer").get("name").asString()).isEqualTo("ការរាប់");
        assertThat(catalog.get("objectives").get(0).get("khmer").get("name").asString()).isEqualTo("រាប់វត្ថុ");
        assertThat(catalog.get("objectives").get(0).get("title").asString()).isEqualTo("Counting things");
        assertThat(jdbc.queryForObject("select count(*) from content_localizations where content_type = 'SKILL' and content_id = ?::uuid",
                Integer.class, UUID.fromString(skillId))).as("the skill's name and description").isEqualTo(2);

        // A family reading Khmer: the catalog, the path and the skills page name everything in Khmer.
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}",
                parent, null), 201).get("id").asString();
        JsonNode offeredKm = find(json(get("/api/v1/children/" + childId + "/curricula", parent, "km"), 200), curriculumId);
        assertThat(offeredKm.get("name").asString()).isEqualTo("លេខដំបូង " + suffix);
        assertThat(offeredKm.get("subjectName").asString()).isEqualTo("លេខ " + suffix);
        JsonNode offeredEn = find(json(get("/api/v1/curricula", parent, "en"), 200), curriculumId);
        assertThat(offeredEn.get("name").asString()).isEqualTo("First numbers " + suffix);
        assertThat(offeredEn.get("subjectName").asString()).isEqualTo("Numbers " + suffix);

        String enrollmentId = json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}",
                parent, null), 201).get("id").asString();
        JsonNode journeyKm = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent, "km"), 200);
        assertThat(journeyKm.get("curriculum").get("name").asString()).isEqualTo("លេខដំបូង " + suffix);
        assertThat(journeyKm.get("curriculum").get("subjectName").asString()).isEqualTo("លេខ " + suffix);
        assertThat(journeyKm.get("chapters").get(0).get("title").asString()).isEqualTo("រាប់វត្ថុ");
        assertThat(journeyKm.get("chapters").get(0).get("description").asString()).isEqualTo("រាប់អ្វីដែលអ្នកឃើញ។");
        assertThat(journeyKm.get("steps").get(0).get("skills").get(0).get("name").asString()).isEqualTo("ការរាប់");
        JsonNode journeyEn = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent, "en"), 200);
        assertThat(journeyEn.get("curriculum").get("name").asString()).isEqualTo("First numbers " + suffix);
        assertThat(journeyEn.get("chapters").get(0).get("title").asString()).isEqualTo("Counting things");
        assertThat(journeyEn.get("steps").get(0).get("skills").get(0).get("name").asString()).isEqualTo("Counting objects");

        JsonNode chapterKm = enrolled(json(get("/api/v1/children/" + childId + "/skills", parent, "km"), 200), enrollmentId).get("objectives").get(0);
        assertThat(chapterKm.get("title").asString()).isEqualTo("រាប់វត្ថុ");
        assertThat(chapterKm.get("skills").get(0).get("name").asString()).isEqualTo("ការរាប់");
        assertThat(chapterKm.get("skills").get(0).get("description").asString()).isEqualTo("រាប់ដល់ដប់។");
        JsonNode chapterEn = enrolled(json(get("/api/v1/children/" + childId + "/skills", parent, "en"), 200), enrollmentId).get("objectives").get(0);
        assertThat(chapterEn.get("skills").get(0).get("name").asString()).isEqualTo("Counting objects");

        // Clearing a Khmer name removes it: the reader gets the English again.
        status(put("/api/v1/studio/curricula/" + curriculumId, "{\"name\":\"First numbers " + suffix + "\",\"khmer\":{\"name\":\" \"}}",
                admin, null), 204);
        assertThat(json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent, "km"), 200).get("curriculum").get("name")
                .asString()).isEqualTo("First numbers " + suffix);
        assertThat(jdbc.queryForObject("select count(*) from content_localizations where content_type = 'CURRICULUM' and content_id = ?::uuid",
                Integer.class, UUID.fromString(curriculumId))).isZero();

        // Only the Studio writes names: a parent can't.
        status(put("/api/v1/studio/skills/" + skillId, "{\"name\":\"Counting\",\"khmer\":{\"name\":\"x\"}}", parent, null), 403);
    }

    /**
     * Khmer names cost a Khmer reader a few reads per curriculum shown, not one per name: a chapter of six skills, all
     * named in Khmer, read in Khmer takes a fixed few statements more than in English (ReadQueryScalingIntegrationTests
     * guards the English reads).
     */
    @Test
    void khmerNamesAreReadTogetherNotOneByOne() throws Exception {
        String admin = staff();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"KMS_" + suffix + "\",\"name\":\"Shapes " + suffix
                + "\",\"khmer\":{\"name\":\"រូបរាង " + suffix + "\"}}", admin, null), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"SH_" + suffix
                + "\",\"title\":\"Shapes\",\"khmer\":{\"name\":\"រូបរាង\"}}", admin, null), 201).get("id").asString();
        StringBuilder skillIds = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"SH" + i + "_" + suffix
                    + "\",\"name\":\"Shape " + i + "\",\"description\":\"Shape " + i + ".\",\"khmer\":{\"name\":\"រូប " + i
                    + "\",\"description\":\"រូប " + i + "។\"}}", admin, null), 201).get("id").asString();
            json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin, null), 200);
            skillIds.append(i == 0 ? "" : ",").append('"').append(skillId).append('"');
        }
        String questionVersionId = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"Which is round?\",\"difficultyLevel\":1,"
                + "\"options\":[{\"label\":\"circle\",\"correct\":true},{\"label\":\"square\",\"correct\":false}],\"skillIds\":["
                + skillIds + "]}", admin, null), 201).get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/question-versions/" + questionVersionId + "/publish", "", admin, null), 200);
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Shapes\"}", admin, null), 201)
                .get("id").asString();
        String activityVersionId = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Find the circle\",\"skillIds\":[" + skillIds + "]}", admin, null), 201)
                .get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + questionVersionId + "\"}]}", admin, null), 200);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin, null), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("KMS_" + suffix, "Shapes " + suffix, 90, 99, 99)).getId().toString();
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"ageHubId\":\"" + ageHubId
                + "\",\"name\":\"Shapes " + suffix + "\",\"khmer\":{\"name\":\"រូបរាង " + suffix + "\"}}", admin, null), 201);
        String curriculumVersion = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/objectives", "{\"objectiveIds\":[\"" + objectiveId + "\"]}",
                admin, null), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/publish", "", admin, null), 200);
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}",
                parent, null), 201).get("id").asString();
        String enrollmentId = json(post("/api/v1/children/" + childId + "/enrollments",
                "{\"curriculumId\":\"" + curriculum.get("id").asString() + "\"}", parent, null), 201).get("id").asString();

        // Per curriculum shown, a Khmer reader costs a fixed few reads: its chapters' names (one query), each chapter's
        // skills' names (one query), the curriculum's and the subject's name - and on the path, the step's own words.
        // Read one name at a time, the six skills alone would cost six.
        int curricula = json(get("/api/v1/children/" + childId + "/skills", parent, "en"), 200).size(); // with the automatic one
        Map<String, Integer> allowance = Map.of("/api/v1/enrollments/" + enrollmentId + "/journey", 4 + 1,
                "/api/v1/children/" + childId + "/skills", 4 * curricula);
        for (Map.Entry<String, Integer> page : allowance.entrySet()) {
            long english = statements(page.getKey(), parent, "en");
            long khmer = statements(page.getKey(), parent, "km");
            System.out.println("SQL statements for " + page.getKey().replaceAll("[0-9a-f-]{36}", "{id}") + ": en " + english + ", km " + khmer);
            assertThat(khmer - english).as("%s: Khmer names read together (en %s, km %s)", page.getKey(), english, khmer)
                    .isLessThanOrEqualTo(page.getValue());
        }
        // ...and they are the Khmer.
        JsonNode chapter = enrolled(json(get("/api/v1/children/" + childId + "/skills", parent, "km"), 200), enrollmentId)
                .get("objectives").get(0);
        assertThat(chapter.get("skills")).hasSize(6);
        chapter.get("skills").forEach(skill -> assertThat(skill.get("name").asString()).startsWith("រូប "));
    }

    private long statements(String path, String bearer, String language) throws Exception {
        json(get(path, bearer, language), 200); // warm
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        json(get(path, bearer, language), 200);
        return stats.getPrepareStatementCount();
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode find(JsonNode list, String id) {
        for (JsonNode node : list) {
            if (id.equals(node.get("id").asString())) {
                return node;
            }
        }
        throw new AssertionError("not listed: " + id + " in " + list);
    }

    /** The skills page's entry for one enrolment (a child may also be enrolled automatically elsewhere). */
    private static JsonNode enrolled(JsonNode progress, String enrollmentId) {
        for (JsonNode node : progress) {
            if (enrollmentId.equals(node.get("enrollmentId").asString())) {
                return node;
            }
        }
        throw new AssertionError("no progress for " + enrollmentId + " in " + progress);
    }

    private String staff() throws Exception {
        String email = "catalog-names-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null, null), 200)
                .get("accessToken").asString();
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"catalog-names-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201).get("accessToken").asString();
    }

    private static void status(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        status(response, expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer, language);
    }

    private HttpResponse<String> post(String path, String body, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer, language);
    }

    private HttpResponse<String> put(String path, String body, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer, language);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer, String language) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (language != null) {
            request.header("Accept-Language", language);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
