package com.platform.content;

import com.platform.PaidSchools;
import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolStaff;
import com.platform.school.repository.SchoolStaffRepository;
import com.platform.school.service.SchoolService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V85 through the real sharing workflow (M5b): a colleague's copy of a shared lesson keeps its English and Khmer
 * titles, and each copy's titles are then its own - editing the copy's leaves the original's as they were.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SharedLessonKhmerTitleIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Shared-Khmer-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Staff(UUID id, String token) {
    }

    @Test
    void aCopiedLessonKeepsBothTitlesAndEachIsThenItsOwn() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"SKT_" + tag + "\",\"name\":\"Share " + tag + "\"}", admin), 201)
                .get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"SKTS_" + tag
                + "\",\"name\":\"Animals\"}", admin), 201).get("id").asString();
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff author = staff(PlatformRole.TEACHER);
        Staff colleague = staff(PlatformRole.TEACHER);
        School school = schoolService.createSchool("Lotus " + tag, principal.id());
        PaidSchools.pay(context, school.getId());
        join(school, author, principal);
        join(school, colleague, principal);

        // The author's lesson, titled in both languages, with one published activity, shared with the school.
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Farm animals\"}",
                author.token()), 201).get("id").asString();
        status(put("/api/v1/studio/lessons/" + lessonId, "{\"name\":\"Farm animals\",\"khmer\":{\"name\":\"សត្វកសិដ្ឋាន\"}}",
                author.token()), 204);
        String activityVersion = json(post("/api/v1/studio/lessons/" + lessonId + "/activities", "{\"title\":\"Who says moo?\","
                + "\"skillIds\":[\"" + skillId + "\"]}", author.token()), 201).get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"Which animal says moo?\",\"options\":[{\"label\":\"Cow\","
                + "\"correct\":true},{\"label\":\"Cat\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"]}", author.token()), 201);
        json(post("/api/v1/studio/activity-versions/" + activityVersion + "/publish-with-questions", "", author.token()), 200);
        status(put("/api/v1/studio/lessons/" + lessonId + "/visibility", "{\"visibility\":\"SCHOOL\"}", author.token()), 204);

        // The colleague copies it: both titles come along.
        String copyId = json(post("/api/v1/studio/shared-lessons/" + lessonId + "/copy", "", colleague.token()), 201).get("id").asString();
        JsonNode copy = lesson(colleague.token(), subjectId, copyId);
        assertThat(copy.get("title").asString()).isEqualTo("Farm animals");
        assertThat(copy.get("khmer").get("name").asString()).isEqualTo("សត្វកសិដ្ឋាន");
        assertThat(copy.get("copied").asBoolean()).isTrue();

        // Each copy's titles are its own: renaming the copy in both languages leaves the original's alone.
        status(put("/api/v1/studio/lessons/" + copyId, "{\"name\":\"Animals at the farm\",\"khmer\":{\"name\":\"សត្វនៅកសិដ្ឋាន\"}}",
                colleague.token()), 204);
        JsonNode renamed = lesson(colleague.token(), subjectId, copyId);
        assertThat(renamed.get("title").asString()).isEqualTo("Animals at the farm");
        assertThat(renamed.get("khmer").get("name").asString()).isEqualTo("សត្វនៅកសិដ្ឋាន");
        JsonNode original = lesson(author.token(), subjectId, lessonId);
        assertThat(original.get("title").asString()).isEqualTo("Farm animals");
        assertThat(original.get("khmer").get("name").asString()).isEqualTo("សត្វកសិដ្ឋាន");
        // ...and the other way round: the author's Khmer rename doesn't reach the copy.
        status(put("/api/v1/studio/lessons/" + lessonId, "{\"name\":\"Farm animals\",\"khmer\":{\"name\":\"សត្វនៅវាលស្រែ\"}}",
                author.token()), 204);
        assertThat(lesson(colleague.token(), subjectId, copyId).get("khmer").get("name").asString()).isEqualTo("សត្វនៅកសិដ្ឋាន");
    }

    private JsonNode lesson(String token, String subjectId, String lessonId) throws Exception {
        for (JsonNode lesson : json(get("/api/v1/studio/lessons?subjectId=" + subjectId, token), 200).get("items")) {
            if (lessonId.equals(lesson.get("id").asString())) {
                return lesson;
            }
        }
        throw new AssertionError("lesson " + lessonId + " not listed");
    }

    // ------------------------------------------------------------------ helpers

    private void join(School school, Staff teacher, Staff invitedBy) {
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacher.id(), invitedBy.id(), Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "shared-khmer-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\"}", null), 200).get("accessToken").asString());
    }

    private static void status(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        status(response, expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer);
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
