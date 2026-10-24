package com.platform.content;

import com.platform.PaidSchools;
import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.repository.AgeHubRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5b (D-13, D-14) over real HTTP against PostgreSQL. A teacher shares a lesson with their school; a colleague at
 * that (paid) school finds it and copies it into their own content - drafts, private, crediting the original, with
 * the questions' choices, media (now the colleague's own), skills and translations; a teacher elsewhere, or only at an
 * unpaid school, neither finds nor copies it; only the author shares it; unsharing hides it and leaves copies alone.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class LessonSharingIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Lesson-Sharing-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private com.platform.school.service.SchoolLifecycleService schoolLifecycle;
    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Staff(UUID id, String token) {
    }

    @Test
    void aColleagueCopiesASharedLessonIntoTheirOwnDraftsAndNobodyElseSeesIt() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"SHARE_" + tag + "\",\"name\":\"Share " + tag + "\"}", admin), 201)
                .get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"ANIMALS_" + tag
                + "\",\"name\":\"Name animals\",\"description\":\"Names common animals.\"}", admin), 201).get("id").asString();
        ageHubRepository.save(AgeHub.create("SHARE_" + tag, "Share " + tag, 90, 99, 99));

        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff author = staff(PlatformRole.TEACHER);
        Staff colleague = staff(PlatformRole.TEACHER);
        Staff elsewhere = staff(PlatformRole.TEACHER);
        Staff unpaidColleague = staff(PlatformRole.TEACHER);
        School school = schoolService.createSchool("Lotus " + tag, principal.id());
        PaidSchools.pay(context, school.getId());
        School other = schoolService.createSchool("River " + tag, staff(PlatformRole.PRINCIPAL).id());
        PaidSchools.pay(context, other.getId());
        School unpaid = schoolService.createSchool("Unpaid " + tag, staff(PlatformRole.PRINCIPAL).id());
        join(school, author, principal);
        join(school, colleague, principal);
        join(other, elsewhere, principal);
        join(unpaid, author, principal);
        join(unpaid, unpaidColleague, principal);

        // The author writes and publishes a lesson with a picture question.
        String picture = json(upload(png(6, 6), author.token()), 201).get("id").asString();
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Farm animals\"}",
                author.token()), 201).get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities", "{\"title\":\"Who says moo?\",\"skillIds\":[\""
                + skillId + "\"]}", author.token()), 201);
        String activityVersion = activity.get("versions").get(0).get("id").asString();
        JsonNode withQuestion = json(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"Which animal says moo?\",\"promptImageAssetId\":\"" + picture
                + "\",\"promptImageDescription\":\"A farm\",\"hint\":\"It gives milk.\",\"options\":[{\"label\":\"Cow\",\"correct\":true,"
                + "\"imageAssetId\":\"" + picture + "\"},{\"label\":\"Cat\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"]}",
                author.token()), 201);
        String questionVersion = withQuestion.get("versions").get(0).get("items").get(0).get("questionVersionId").asString();
        jdbc.update("INSERT INTO content_localizations (id, content_type, content_id, locale, field_name, localized_text) "
                + "VALUES (gen_random_uuid(), 'QUESTION_VERSION', ?::uuid, 'km', 'prompt', 'សត្វមួយណាយំម៉ូ?')", questionVersion);
        json(post("/api/v1/studio/activity-versions/" + activityVersion + "/publish-with-questions", "", author.token()), 200);
        String shared = "/api/v1/studio/shared-lessons?subjectId=" + subjectId;

        // Private until the author shares it; only the author can.
        assertThat(json(get(shared, colleague.token()), 200)).isEmpty();
        assertThat(put("/api/v1/studio/lessons/" + lessonId + "/visibility", "{\"visibility\":\"SCHOOL\"}", colleague.token()).statusCode())
                .isEqualTo(403);
        assertThat(put("/api/v1/studio/lessons/" + lessonId + "/visibility", "{\"visibility\":\"SCHOOL\"}", principal.token()).statusCode())
                .as("a principal may manage their staff's content, but sharing is the author's").isEqualTo(403);
        assertThat(put("/api/v1/studio/lessons/" + lessonId + "/visibility", "{\"visibility\":\"PUBLIC\"}", author.token()).statusCode())
                .as("PUBLIC is deferred").isEqualTo(400);
        assertThat(put("/api/v1/studio/lessons/" + lessonId + "/visibility", "{\"visibility\":\"SCHOOL\"}", author.token()).statusCode())
                .isEqualTo(204);

        // Found by a colleague at the paid school (and its principal); not elsewhere, nor through an unpaid school.
        JsonNode found = json(get(shared, colleague.token()), 200);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("title").asString()).isEqualTo("Farm animals");
        assertThat(found.get(0).get("schools").get(0).asString()).isEqualTo("Lotus " + tag);
        assertThat(found.get(0).get("activities").get(0).get("questions").asInt()).isEqualTo(1);
        assertThat(found.toString()).doesNotContain("Cow").doesNotContain("correct");
        assertThat(json(get(shared, principal.token()), 200)).hasSize(1);
        assertThat(json(get(shared, elsewhere.token()), 200)).isEmpty();
        assertThat(json(get(shared, unpaidColleague.token()), 200)).isEmpty();
        assertThat(json(get(shared, admin), 200)).isEmpty();
        String copyPath = "/api/v1/studio/shared-lessons/" + lessonId + "/copy";
        assertThat(post(copyPath, "", elsewhere.token()).statusCode()).as("another school").isEqualTo(404);
        assertThat(post(copyPath, "", unpaidColleague.token()).statusCode()).as("an unpaid school").isEqualTo(404);
        assertThat(post(copyPath, "", author.token()).statusCode()).as("their own").isEqualTo(404);

        // The copy: the colleague's own private drafts, the same content, their own media, the translation kept.
        JsonNode copy = json(post(copyPath, "", colleague.token()), 201);
        String copyId = copy.get("id").asString();
        assertThat(copy.get("visibility").asString()).isEqualTo("PRIVATE");
        assertThat(copy.get("copied").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select owner_account_id from lessons where id = ?::uuid", UUID.class, copyId)).isEqualTo(colleague.id());
        assertThat(jdbc.queryForObject("select copied_from_account_id from lessons where id = ?::uuid", UUID.class, copyId)).isEqualTo(author.id());
        JsonNode lessons = json(get("/api/v1/studio/lessons?subjectId=" + subjectId, colleague.token()), 200).get("items");
        assertThat(lessons).hasSize(1);
        String copiedActivity = lessons.get(0).get("activities").get(0).get("id").asString();
        JsonNode copiedDetail = json(get("/api/v1/studio/activities/" + copiedActivity, colleague.token()), 200);
        JsonNode copiedVersion = copiedDetail.get("versions").get(0);
        assertThat(copiedVersion.get("status").asString()).isEqualTo("DRAFT");
        assertThat(copiedVersion.get("skillIds").get(0).asString()).isEqualTo(skillId);
        JsonNode item = copiedVersion.get("items").get(0);
        assertThat(item.get("questionVersionId").asString()).isNotEqualTo(questionVersion);
        assertThat(item.get("questionVersionStatus").asString()).isEqualTo("DRAFT");
        JsonNode question = json(get("/api/v1/studio/questions/" + item.get("questionId").asString(), colleague.token()), 200);
        JsonNode version = question.get("versions").get(0);
        assertThat(version.get("prompt").asString()).isEqualTo("Which animal says moo?");
        assertThat(version.get("hint").asString()).isEqualTo("It gives milk.");
        assertThat(version.get("options").toString()).contains("Cow", "Cat");
        String copiedPicture = version.get("promptImage").get("id").asString();
        assertThat(copiedPicture).isNotEqualTo(picture);
        assertThat(jdbc.queryForObject("select owner_account_id from content_assets where id = ?::uuid", UUID.class, copiedPicture))
                .isEqualTo(colleague.id());
        assertThat(jdbc.queryForObject("select count(*) from content_assets where owner_account_id = ? and sha256 = "
                + "(select sha256 from content_assets where id = ?::uuid)", Integer.class, colleague.id(), picture)).as("one copy of one file")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select localized_text from content_localizations where content_type = 'QUESTION_VERSION' "
                + "and content_id = ?::uuid", String.class, version.get("id").asString())).isEqualTo("សត្វមួយណាយំម៉ូ?");
        // ...and theirs to edit and publish, through the same checks as anything they write.
        json(post("/api/v1/studio/activity-versions/" + copiedVersion.get("id").asString() + "/publish-with-questions", "",
                colleague.token()), 200);
        // The author's lesson is untouched.
        assertThat(jdbc.queryForObject("select count(*) from activities where lesson_id = ?::uuid", Integer.class, lessonId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select owner_account_id from content_assets where id = ?::uuid", UUID.class, picture))
                .isEqualTo(author.id());
        // The author changes the source afterwards: the copy keeps what it was copied from.
        String sourceQuestion = withQuestion.get("versions").get(0).get("items").get(0).get("questionId").asString();
        JsonNode reopened = json(post("/api/v1/studio/questions/" + sourceQuestion + "/draft", "", author.token()), 200);
        String newDraft = reopened.get("versions").get(reopened.get("versions").size() - 1).get("id").asString();
        json(put("/api/v1/studio/question-versions/" + newDraft, "{\"prompt\":\"Which animal says baa?\",\"options\":["
                + "{\"label\":\"Sheep\",\"correct\":true},{\"label\":\"Cat\",\"correct\":false}]}", author.token()), 200);
        json(post("/api/v1/studio/question-versions/" + newDraft + "/publish", "", author.token()), 200);
        JsonNode copiedNow = json(get("/api/v1/studio/questions/" + item.get("questionId").asString(), colleague.token()), 200);
        assertThat(copiedNow.get("versions").get(0).get("prompt").asString()).isEqualTo("Which animal says moo?");
        assertThat(copiedNow.get("versions").get(0).get("options").toString()).contains("Cow");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'LESSON_COPIED' and actor_id = ?", Integer.class,
                colleague.id())).isEqualTo(1);

        // Unshared: no longer found or copied; the copy stays the colleague's.
        assertThat(put("/api/v1/studio/lessons/" + lessonId + "/visibility", "{\"visibility\":\"PRIVATE\"}", author.token()).statusCode())
                .isEqualTo(204);
        assertThat(json(get(shared, colleague.token()), 200)).isEmpty();
        assertThat(post(copyPath, "", colleague.token()).statusCode()).isEqualTo(404);
        assertThat(json(get("/api/v1/studio/lessons?subjectId=" + subjectId, colleague.token()), 200).get("items")).hasSize(1);

        // Shared again, then the school is archived: its staff are no longer colleagues for sharing.
        assertThat(put("/api/v1/studio/lessons/" + lessonId + "/visibility", "{\"visibility\":\"SCHOOL\"}", author.token()).statusCode())
                .isEqualTo(204);
        assertThat(json(get(shared, colleague.token()), 200)).hasSize(1);
        schoolLifecycle.archiveSchool(principal.id(), school.getId());
        assertThat(json(get(shared, colleague.token()), 200)).isEmpty();
        assertThat(post(copyPath, "", colleague.token()).statusCode()).as("an archived school").isEqualTo(404);
    }

    @Test
    void aCopyThatWouldExceedTheColleaguesStorageIsRefusedWholeAndAPlatformLessonIsNeverShared() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"QUOTA_" + tag + "\",\"name\":\"Quota " + tag + "\"}", admin), 201)
                .get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"Q_" + tag
                + "\",\"name\":\"Count\",\"description\":\"Counts.\"}", admin), 201).get("id").asString();
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff author = staff(PlatformRole.TEACHER);
        Staff colleague = staff(PlatformRole.TEACHER);
        School school = schoolService.createSchool("Quota " + tag, principal.id());
        PaidSchools.pay(context, school.getId());
        join(school, author, principal);
        join(school, colleague, principal);
        String picture = json(upload(png(8, 8), author.token()), 201).get("id").asString();
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Counting\"}",
                author.token()), 201).get("id").asString();
        String activityVersion = json(post("/api/v1/studio/lessons/" + lessonId + "/activities", "{\"title\":\"How many?\",\"skillIds\":[\""
                + skillId + "\"]}", author.token()), 201).get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"How many dots?\",\"promptImageAssetId\":\"" + picture
                + "\",\"promptImageDescription\":\"Dots\",\"options\":[{\"label\":\"3\",\"correct\":true},{\"label\":\"4\","
                + "\"correct\":false}],\"skillIds\":[\"" + skillId + "\"]}", author.token()), 201);
        json(post("/api/v1/studio/activity-versions/" + activityVersion + "/publish-with-questions", "", author.token()), 200);
        assertThat(put("/api/v1/studio/lessons/" + lessonId + "/visibility", "{\"visibility\":\"SCHOOL\"}", author.token()).statusCode())
                .isEqualTo(204);

        // No room for the picture: refused, and nothing of the copy is left behind.
        jdbc.update("INSERT INTO account_storage_allowances (id, account_id, limit_mb, note, set_by_account_id) "
                + "VALUES (gen_random_uuid(), ?, 0, 'Test: no storage', ?)", colleague.id(), colleague.id());
        HttpResponse<String> refused = post("/api/v1/studio/shared-lessons/" + lessonId + "/copy", "", colleague.token());
        assertThat(refused.statusCode()).as(refused.body()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from lessons where owner_account_id = ?", Integer.class, colleague.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from questions where owner_account_id = ?", Integer.class, colleague.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from content_assets where owner_account_id = ?", Integer.class, colleague.id())).isZero();
        jdbc.update("DELETE FROM account_storage_allowances WHERE account_id = ?", colleague.id());
        json(post("/api/v1/studio/shared-lessons/" + lessonId + "/copy", "", colleague.token()), 201);

        // A platform lesson (an administrator's) isn't shared with a school.
        String platformLesson = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Platform\"}", admin),
                201).get("id").asString();
        assertThat(put("/api/v1/studio/lessons/" + platformLesson + "/visibility", "{\"visibility\":\"SCHOOL\"}", admin).statusCode())
                .isEqualTo(409);
    }

    // ------------------------------------------------------------------ helpers

    private void join(School school, Staff teacher, Staff invitedBy) {
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacher.id(), invitedBy.id(), Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "share-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\"}", null), 200).get("accessToken").asString());
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, (int) (Math.random() * 0xFFFFFF));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> upload(byte[] content, String bearer) throws Exception {
        String boundary = "bk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"farm.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes());
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes());
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/studio/media"))
                .header("Authorization", "Bearer " + bearer).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
