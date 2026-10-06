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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X11 over real HTTP against PostgreSQL: pictures and recordings are inspected from their bytes,
 * owned like content, attached only where their owner's content may use them and only while a
 * version is a draft, and reached by a child's browser through signed URLs alone. ORDERING
 * questions are authored, published only when there is something to order, never handed to a
 * child already in order, and scored on what the items read - with every refusal leaving no trace.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class QuestionMediaAndOrderingIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Media-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void mediaIsInspectedFromItsBytesOwnedAndServedOnlyThroughASignedUrl() throws Exception {
        Staff teacher = staff(PlatformRole.TEACHER);
        int before = assetCount();

        // Not staff, not media, too large: refused, nothing kept.
        assertThat(upload("/api/v1/studio/media", png(4, 4), "a.png", registerParent()).statusCode()).isEqualTo(403);
        HttpResponse<String> text = upload("/api/v1/studio/media", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8),
                "picture.png", teacher.token());
        assertThat(text.statusCode()).isEqualTo(422);
        assertThat(JSON.readTree(text.body()).get("detail").asString()).contains("PNG, JPEG or WebP");
        HttpResponse<String> huge = upload("/api/v1/studio/media", pngHeader(5000, 20), "wide.png", teacher.token());
        assertThat(huge.statusCode()).isEqualTo(422);
        assertThat(JSON.readTree(huge.body()).get("detail").asString()).contains("4096");
        byte[] heavy = Arrays.copyOf(pngHeader(100, 100), 2 * 1024 * 1024 + 10);
        assertThat(JSON.readTree(upload("/api/v1/studio/media", heavy, "heavy.png", teacher.token()).body()).get("detail").asString())
                .contains("at most 2 MB");
        assertThat(upload("/api/v1/studio/media", new byte[6 * 1024 * 1024], "big.wav", teacher.token()).statusCode()).isEqualTo(413);
        assertThat(assetCount()).isEqualTo(before);

        // A picture: typed from its bytes whatever its name says; the same bytes again are the same asset.
        byte[] picture = png(6, 4);
        JsonNode image = json(upload("/api/v1/studio/media", picture, "photo.mp3", teacher.token()), 201);
        assertThat(image.get("type").asString()).isEqualTo("IMAGE");
        assertThat(image.get("mimeType").asString()).isEqualTo("image/png");
        assertThat(json(upload("/api/v1/studio/media", picture, "again.png", teacher.token()), 201).get("id").asString())
                .isEqualTo(image.get("id").asString());
        assertThat(assetCount()).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("select owner_account_id from content_assets where id = ?::uuid", UUID.class,
                image.get("id").asString())).isEqualTo(teacher.id());
        JsonNode audio = json(upload("/api/v1/studio/media", wav(), "sound.png", teacher.token()), 201);
        assertThat(audio.get("type").asString()).isEqualTo("AUDIO");
        assertThat(audio.get("mimeType").asString()).isEqualTo("audio/wav");

        // Served by its signed URL to anyone holding it (an <img> sends no token), as the inspected type, sandboxed.
        String url = image.get("url").asString();
        HttpResponse<byte[]> served = http.send(HttpRequest.newBuilder(uri(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).isEqualTo(picture);
        assertThat(served.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(served.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(served.headers().firstValue("Content-Security-Policy").orElse("")).contains("sandbox");
        assertThat(served.headers().firstValue("Cache-Control").orElse("")).contains("private");
        HttpResponse<byte[]> range = http.send(HttpRequest.newBuilder(uri(audio.get("url").asString())).header("Range", "bytes=0-3")
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(range.statusCode()).isEqualTo(206);
        assertThat(new String(range.body(), StandardCharsets.US_ASCII)).isEqualTo("RIFF");

        // Anything not exactly as issued: 404 alike.
        String tampered = url.substring(0, url.length() - 2) + (url.endsWith("AA") ? "BB" : "AA");
        String laterExpiry = url.replaceFirst("e=(\\d+)", "e=" + (Long.parseLong(url.replaceAll(".*e=(\\d+).*", "$1")) + 3600));
        String otherAsset = url.replace(image.get("id").asString(), audio.get("id").asString());
        for (String forged : List.of(tampered, laterExpiry, otherAsset, "/api/v1/media/" + image.get("id").asString() + "?e=1&s=x")) {
            assertThat(get(forged, null).statusCode()).as(forged).isEqualTo(404);
        }
        // The rest of the API stays closed.
        assertThat(get("/api/v1/studio/subjects", null).statusCode()).isEqualTo(401);
    }

    @Test
    void mediaIsAttachedOnlyWhereItsOwnerSContentMayUseItOfTheRightKindAndOnlyToADraft() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        Staff teacher = staff(PlatformRole.TEACHER);
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        Catalog catalog = catalog(admin.token());
        String mine = json(upload("/api/v1/studio/media", png(5, 5), "mine.png", teacher.token()), 201).get("id").asString();
        String theirs = json(upload("/api/v1/studio/media", png(7, 7), "theirs.png", otherTeacher.token()), 201).get("id").asString();
        String sound = json(upload("/api/v1/studio/media", wav(), "s.wav", teacher.token()), 201).get("id").asString();
        String platform = json(upload("/api/v1/studio/media", png(9, 9), "platform.png", admin.token()), 201).get("id").asString();
        int questionsBefore = questionCount(catalog.subjectId());

        // Another author's picture, a recording where a picture goes, an unknown asset: refused, no question written.
        assertThat(post("/api/v1/studio/questions", multipleChoice(catalog, theirs, null, null), teacher.token()).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/studio/questions", multipleChoice(catalog, sound, null, null), teacher.token()).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/studio/questions", multipleChoice(catalog, null, null, UUID.randomUUID().toString()), teacher.token())
                .statusCode()).isEqualTo(404);
        assertThat(questionCount(catalog.subjectId())).isEqualTo(questionsBefore);

        // Their own, and platform media, are fine; the author sees them with a URL to preview.
        JsonNode question = json(post("/api/v1/studio/questions", multipleChoice(catalog, mine, sound, platform), teacher.token()), 201);
        JsonNode version = question.get("versions").get(0);
        assertThat(version.get("promptImage").get("id").asString()).isEqualTo(mine);
        assertThat(version.get("promptAudio").get("id").asString()).isEqualTo(sound);
        assertThat(version.get("options").get(0).get("image").get("id").asString()).isEqualTo(platform);
        assertThat(version.get("promptImage").get("url").asString()).startsWith("/api/v1/media/" + mine + "?e=");
        JsonNode preview = json(get("/api/v1/studio/question-versions/" + version.get("id").asString() + "/preview", teacher.token()), 200);
        assertThat(preview.get("promptAudio").get("type").asString()).isEqualTo("AUDIO");
        assertThat(preview.get("options").get(0).get("image").get("url").asString()).contains(platform);

        // A picture nobody described can't reach a child.
        String undescribed = firstVersion(json(post("/api/v1/studio/questions", multipleChoice(catalog, mine, null, null)
                .replace("\"promptImageDescription\":\"A garden with animals\",", ""), teacher.token()), 201));
        assertThat(post("/api/v1/studio/question-versions/" + undescribed + "/publish", "", teacher.token()).body())
                .contains("Describe the question's picture");

        // Published: frozen. A new draft starts with the same media.
        String versionId = version.get("id").asString();
        json(post("/api/v1/studio/question-versions/" + versionId + "/publish", "", teacher.token()), 200);
        assertThat(put("/api/v1/studio/question-versions/" + versionId, revise(null), teacher.token()).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select prompt_image_asset_id from question_versions where id = ?::uuid", UUID.class, versionId))
                .isEqualTo(UUID.fromString(mine));
        JsonNode draft = json(post("/api/v1/studio/questions/" + question.get("id").asString() + "/draft", "", teacher.token()), 200)
                .get("versions").get(1);
        assertThat(draft.get("promptImage").get("id").asString()).isEqualTo(mine);
        assertThat(draft.get("options").get(0).get("image").get("id").asString()).isEqualTo(platform);

        // On the draft the picture can go; another teacher can't touch it at all.
        assertThat(put("/api/v1/studio/question-versions/" + draft.get("id").asString(), revise(null), otherTeacher.token()).statusCode())
                .isEqualTo(403);
        JsonNode revised = json(put("/api/v1/studio/question-versions/" + draft.get("id").asString(), revise(null), teacher.token()), 200);
        assertThat(revised.get("versions").get(1).get("promptImage").isNull()).isTrue();
    }

    @Test
    void anOrderingQuestionIsPlayedFromAScrambledStartAndScoredOnWhatTheItemsRead() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin.token());
        JsonNode picture = json(upload("/api/v1/studio/media", png(8, 8), "book.png", admin.token()), 201);
        JsonNode reading = json(upload("/api/v1/studio/media", wav(), "book.wav", admin.token()), 201);

        // Two items isn't something to order; all the same isn't either.
        String tooFew = firstVersion(json(post("/api/v1/studio/questions", ordering(catalog, "Spell it", List.of("a", "b"), null, null),
                admin.token()), 201));
        assertThat(publishErrors(post("/api/v1/studio/question-versions/" + tooFew + "/publish", "", admin.token())))
                .anyMatch(body -> body.contains("needs 3 to 8 items"));
        String same = firstVersion(json(post("/api/v1/studio/questions", ordering(catalog, "Same", List.of("o", "o", "o"), null, null),
                admin.token()), 201));
        assertThat(publishErrors(post("/api/v1/studio/question-versions/" + same + "/publish", "", admin.token())))
                .anyMatch(body -> body.contains("all the same"));

        // "book": two interchangeable o's, with a picture and the word read aloud.
        JsonNode book = json(post("/api/v1/studio/questions", ordering(catalog, "Build the word", List.of("b", "o", "o", "k"),
                picture.get("id").asString(), reading.get("id").asString()), admin.token()), 201);
        String bookVersion = firstVersion(book);
        assertThat(labels(book.get("versions").get(0).get("items"))).containsExactly("b", "o", "o", "k");
        json(post("/api/v1/studio/question-versions/" + bookVersion + "/publish", "", admin.token()), 200);
        String numbers = firstVersion(json(post("/api/v1/studio/questions", ordering(catalog, "Smallest first", List.of("1", "2", "3"),
                null, null), admin.token()), 201));
        json(post("/api/v1/studio/question-versions/" + numbers + "/publish", "", admin.token()), 200);

        Played played = playableActivity(admin.token(), catalog, List.of(bookVersion, numbers));
        JsonNode content = json(get("/api/v1/activity-versions/" + played.activityVersionId(), played.parent()), 200);
        JsonNode bookItem = itemFor(content, bookVersion);
        JsonNode numberItem = itemFor(content, numbers);

        // Never handed out solved, and nothing in it gives the order away.
        assertThat(labels(bookItem.get("options"))).isNotEqualTo(List.of("b", "o", "o", "k"));
        assertThat(labels(numberItem.get("options"))).isNotEqualTo(List.of("1", "2", "3"));
        assertThat(displayOrders(bookItem.get("options"))).containsExactly(0, 1, 2, 3);
        assertThat(content.toString()).doesNotContain("\"correct\"");
        // ...and the same start every time it is read (deterministic for the version).
        assertThat(labels(itemFor(json(get("/api/v1/activity-versions/" + played.activityVersionId(), played.parent()), 200),
                bookVersion).get("options"))).isEqualTo(labels(bookItem.get("options")));
        // The picture and reading reach the child's browser through their signed URLs.
        String promptUrl = bookItem.get("promptImage").get("url").asString();
        assertThat(http.send(HttpRequest.newBuilder(uri(promptUrl)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                .isEqualTo(200);
        assertThat(bookItem.get("promptAudio").get("mimeType").asString()).isEqualTo("audio/wav");
        assertThat(bookItem.get("promptImageDescription").asString()).isEqualTo("An open book");

        // Not an ordering of every item exactly once: refused, and no evidence written.
        String attempt = played.attemptId();
        List<String> ids = idsInOrder(bookItem.get("options"), List.of("b", "o", "o", "k"));
        for (String invalid : List.of("\"b-o-o-k\"", "[" + quoted(ids.subList(0, 3)) + "]",
                "[" + quoted(List.of(ids.get(0), ids.get(1), ids.get(1), ids.get(3))) + "]", "{\"a\":1}")) {
            HttpResponse<String> refused = post("/api/v1/activity-attempts/" + attempt + "/answers", answer(bookVersion,
                    JSON.writeValueAsString(invalid)), played.parent());
            assertThat(refused.statusCode()).as(invalid).isEqualTo(422);
        }
        assertThat(jdbc.queryForObject("select count(*) from answer_records where activity_attempt_id = ?::uuid", Integer.class, attempt))
                .isZero();

        // The two o's swapped still reads "book": right. The correct order comes back as feedback.
        List<String> swapped = List.of(ids.get(0), ids.get(2), ids.get(1), ids.get(3));
        JsonNode right = json(post("/api/v1/activity-attempts/" + attempt + "/answers", answer(bookVersion,
                JSON.writeValueAsString("[" + quoted(swapped) + "]")), played.parent()), 201);
        assertThat(right.get("correct").asBoolean()).isTrue();
        assertThat(right.get("correctAnswer").get("optionIds")).hasSize(4);
        assertThat(right.get("correctAnswer").get("text").asString()).isEqualTo("b → o → o → k");

        // "3, 2, 1" is wrong - and it is evidence like any answer.
        List<String> backwards = idsInOrder(numberItem.get("options"), List.of("3", "2", "1"));
        JsonNode wrong = json(post("/api/v1/activity-attempts/" + attempt + "/answers", answer(numbers,
                JSON.writeValueAsString("[" + quoted(backwards) + "]")), played.parent()), 201);
        assertThat(wrong.get("correct").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from answer_records where activity_attempt_id = ?::uuid", Integer.class, attempt))
                .isEqualTo(2);
    }

    @Test
    void aSortingQuestionIsServedWithoutItsGroupsGivenAwayAndScoredOnWhereEveryItemWent() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin.token());

        // Refused at publish: one group; a group nothing belongs in; the same item in two groups.
        assertThat(publishErrors(publish(sorting(catalog, List.of("Even"), List.of("2:0", "4:0", "6:0", "8:0")), admin)))
                .anyMatch(body -> body.contains("needs 2 to 4 groups"));
        assertThat(publishErrors(publish(sorting(catalog, List.of("Even", "Odd"), List.of("2:0", "4:0", "6:0", "8:0")), admin)))
                .anyMatch(body -> body.contains("Every group needs at least one item"));
        assertThat(publishErrors(publish(sorting(catalog, List.of("Even", "Odd"), List.of("2:0", "2:1", "6:0", "3:1")), admin)))
                .anyMatch(body -> body.contains("look the same"));

        JsonNode question = json(post("/api/v1/studio/questions",
                sorting(catalog, List.of("Even", "Odd"), List.of("2:0", "4:0", "6:0", "1:1", "3:1", "5:1")), admin.token()), 201);
        String version = firstVersion(question);
        JsonNode authored = question.get("versions").get(0);
        assertThat(labels(authored.get("groups"))).containsExactly("Even", "Odd");
        assertThat(authored.get("sortItems")).hasSize(6);
        assertThat(authored.get("sortItems").get(3).get("group").asInt()).isEqualTo(1);
        json(post("/api/v1/studio/question-versions/" + version + "/publish", "", admin.token()), 200);

        Played played = playableActivity(admin.token(), catalog, List.of(version));
        JsonNode content = json(get("/api/v1/activity-versions/" + played.activityVersionId(), played.parent()), 200);
        JsonNode item = itemFor(content, version);
        List<JsonNode> groups = new ArrayList<>();
        List<JsonNode> items = new ArrayList<>();
        item.get("options").forEach(o -> (o.get("sortRole").asString().equals("GROUP") ? groups : items).add(o));
        assertThat(groups.stream().map(g -> g.get("label").asString()).toList()).containsExactly("Even", "Odd");
        assertThat(items).hasSize(6);
        // Which group an item belongs to never leaves the server, and items aren't served grouped.
        assertThat(item.toString()).doesNotContain("sortGroup").doesNotContain("\"group\"").doesNotContain("\"correct\"");
        assertThat(items.stream().map(i -> i.get("label").asString()).toList()).isNotEqualTo(List.of("2", "4", "6", "1", "3", "5"));

        java.util.Map<String, String> id = new java.util.HashMap<>();
        groups.forEach(g -> id.put(g.get("label").asString(), g.get("id").asString()));
        items.forEach(i -> id.put(i.get("label").asString(), i.get("id").asString()));
        String attempt = played.attemptId();
        // Not every item placed, an item put into an item, a made-up group: refused, nothing recorded.
        for (String invalid : List.of(
                "{\"" + id.get("2") + "\":\"" + id.get("Even") + "\"}",
                placement(id, "2:4", "4:Even", "6:Even", "1:Odd", "3:Odd", "5:Odd"),
                placement(id, "2:" + UUID.randomUUID(), "4:Even", "6:Even", "1:Odd", "3:Odd", "5:Odd"),
                "[\"" + id.get("2") + "\"]")) {
            HttpResponse<String> refused = post("/api/v1/activity-attempts/" + attempt + "/answers",
                    answer(version, JSON.writeValueAsString(invalid)), played.parent());
            assertThat(refused.statusCode()).as(invalid).isEqualTo(422);
        }
        assertThat(jdbc.queryForObject("select count(*) from answer_records where activity_attempt_id = ?::uuid", Integer.class, attempt))
                .isZero();

        // Every item in its group: right; the feedback says where each one belongs.
        JsonNode right = json(post("/api/v1/activity-attempts/" + attempt + "/answers", answer(version,
                JSON.writeValueAsString(placement(id, "2:Even", "4:Even", "6:Even", "1:Odd", "3:Odd", "5:Odd"))), played.parent()), 201);
        assertThat(right.get("correct").asBoolean()).isTrue();
        assertThat(right.get("correctAnswer").get("matches")).hasSize(6);
        assertThat(right.get("correctAnswer").get("text").asString()).contains("2 → Even").contains("5 → Odd");
        assertThat(jdbc.queryForObject("select count(*) from answer_records where activity_attempt_id = ?::uuid", Integer.class, attempt))
                .isEqualTo(1);
    }

    private HttpResponse<String> publish(String questionJson, Staff admin) throws Exception {
        String version = firstVersion(json(post("/api/v1/studio/questions", questionJson, admin.token()), 201));
        return post("/api/v1/studio/question-versions/" + version + "/publish", "", admin.token());
    }

    /** {@code items}: "label:groupIndex". */
    private static String sorting(Catalog catalog, List<String> groups, List<String> items) {
        return "{\"subjectId\":\"" + catalog.subjectId() + "\",\"questionType\":\"SORTING\",\"prompt\":\"Even or odd?\","
                + "\"groups\":[" + String.join(",", groups.stream().map(g -> "{\"label\":\"" + g + "\"}").toList()) + "],"
                + "\"sortItems\":[" + String.join(",", items.stream().map(i -> "{\"label\":\"" + i.split(":")[0] + "\",\"group\":"
                + i.split(":")[1] + "}").toList()) + "],\"skillIds\":[\"" + catalog.skillId() + "\"]}";
    }

    /** {@code pairs}: "itemLabel:groupLabel" (or an id in place of either). */
    private static String placement(java.util.Map<String, String> id, String... pairs) {
        return "{" + String.join(",", java.util.Arrays.stream(pairs).map(p -> {
            String[] parts = p.split(":");
            return "\"" + id.getOrDefault(parts[0], parts[0]) + "\":\"" + id.getOrDefault(parts[1], parts[1]) + "\"";
        }).toList()) + "}";
    }

    // ------------------------------------------------------------------ content

    private record Catalog(String suffix, String subjectId, String ageHubId, String skillId, String objectiveId) {
    }

    private Catalog catalog(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"MEDIA_" + suffix + "\",\"name\":\"Media "
                + suffix + "\"}", admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"SPELL_" + suffix
                + "\",\"name\":\"Spell short words\",\"description\":\"Builds short words from letters.\"}", admin), 201)
                .get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"OBJ_" + suffix
                + "\",\"title\":\"Words\"}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("MEDIA_" + suffix, "Media " + suffix, 90, 99, 99)).getId().toString();
        return new Catalog(suffix, subjectId, ageHubId, skillId, objectiveId);
    }

    private static String multipleChoice(Catalog catalog, String promptImage, String promptAudio, String optionImage) {
        return "{\"subjectId\":\"" + catalog.subjectId() + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"Which is the cat?\","
                + "\"promptImageAssetId\":" + idOrNull(promptImage) + ",\"promptImageDescription\":\"A garden with animals\","
                + "\"promptAudioAssetId\":" + idOrNull(promptAudio) + ","
                + "\"options\":[{\"label\":\"A cat sitting\",\"correct\":true,\"imageAssetId\":" + idOrNull(optionImage) + "},"
                + "{\"label\":\"A dog\",\"correct\":false}],\"skillIds\":[\"" + catalog.skillId() + "\"]}";
    }

    private static String revise(String promptImage) {
        return "{\"prompt\":\"Which is the cat?\",\"promptImageAssetId\":" + idOrNull(promptImage)
                + ",\"options\":[{\"label\":\"A cat sitting\",\"correct\":true},{\"label\":\"A dog\",\"correct\":false}]}";
    }

    private static String ordering(Catalog catalog, String prompt, List<String> labels, String promptImage, String promptAudio) {
        return "{\"subjectId\":\"" + catalog.subjectId() + "\",\"questionType\":\"ORDERING\",\"prompt\":\"" + prompt + "\","
                + "\"promptImageAssetId\":" + idOrNull(promptImage) + ",\"promptImageDescription\":\"An open book\","
                + "\"promptAudioAssetId\":" + idOrNull(promptAudio) + ","
                + "\"items\":[" + String.join(",", labels.stream().map(l -> "{\"label\":\"" + l + "\"}").toList()) + "],"
                + "\"skillIds\":[\"" + catalog.skillId() + "\"]}";
    }

    private record Played(String parent, String activityVersionId, String attemptId) {
    }

    /** A published activity holding the questions, on a published curriculum a family's child is enrolled in, attempt started. */
    private Played playableActivity(String admin, Catalog catalog, List<String> questionVersionIds) throws Exception {
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId() + "\",\"title\":\"Words\"}",
                admin), 201).get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Build words\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        String activityVersionId = activity.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items", "{\"items\":[" + String.join(",",
                questionVersionIds.stream().map(id -> "{\"questionVersionId\":\"" + id + "\"}").toList()) + "]}", admin), 200);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin), 200);
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"ageHubId\":\"" + catalog.ageHubId() + "\",\"name\":\"Words " + catalog.suffix() + "\"}", admin), 201);
        String curriculumVersionId = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/objectives",
                "{\"objectiveIds\":[\"" + catalog.objectiveId() + "\"]}", admin), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/publish", "", admin), 200);

        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Media Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
        String enrollmentId = json(post("/api/v1/children/" + childId + "/enrollments",
                "{\"curriculumId\":\"" + curriculum.get("id").asString() + "\"}", parent), 201).get("id").asString();
        JsonNode step = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent), 200).get("steps").get(0);
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + step.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                + step.get("learningPathItemId").asString() + "\"}", parent), 201).get("id").asString();
        return new Played(parent, step.get("activityVersionId").asString(), attemptId);
    }

    private static JsonNode itemFor(JsonNode content, String questionVersionId) {
        for (JsonNode item : content.get("items")) {
            if (item.get("questionVersionId").asString().equals(questionVersionId)) {
                return item;
            }
        }
        throw new AssertionError("no item for " + questionVersionId);
    }

    /** The option ids that read as {@code wanted}, in that order (duplicate labels taken in turn). */
    private static List<String> idsInOrder(JsonNode options, List<String> wanted) {
        List<String> ids = new ArrayList<>();
        for (String label : wanted) {
            for (JsonNode option : options) {
                String id = option.get("id").asString();
                if (option.get("label").asString().equals(label) && !ids.contains(id)) {
                    ids.add(id);
                    break;
                }
            }
        }
        assertThat(ids).hasSize(wanted.size());
        return ids;
    }

    private static List<String> labels(JsonNode items) {
        List<String> labels = new ArrayList<>();
        items.forEach(item -> labels.add(item.get("label").asString()));
        return labels;
    }

    private static List<Integer> displayOrders(JsonNode items) {
        List<Integer> orders = new ArrayList<>();
        items.forEach(item -> orders.add(item.get("displayOrder").asInt()));
        return orders;
    }

    private static String quoted(List<String> ids) {
        return String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList());
    }

    private static String answer(String questionVersionId, String submittedAnswerJson) {
        return "{\"questionVersionId\":\"" + questionVersionId + "\",\"submittedAnswer\":" + submittedAnswerJson
                + ",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";
    }

    private static String idOrNull(String id) {
        return id == null ? "null" : "\"" + id + "\"";
    }

    private static String firstVersion(JsonNode detail) {
        return detail.get("versions").get(0).get("id").asString();
    }

    private static List<String> publishErrors(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isIn(409, 422);
        return List.of(response.body());
    }

    private int assetCount() {
        return jdbc.queryForObject("select count(*) from content_assets", Integer.class);
    }

    private int questionCount(String subjectId) {
        return jdbc.queryForObject("select count(*) from questions where subject_id = ?::uuid", Integer.class, subjectId);
    }

    // ------------------------------------------------------------------ files

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width * 31 + height);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** A PNG signature and header claiming the given size (the rest of the file doesn't matter to inspection). */
    private static byte[] pngHeader(int width, int height) {
        byte[] b = new byte[64];
        byte[] start = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(start, 0, b, 0, start.length);
        b[16] = (byte) (width >>> 24);
        b[17] = (byte) (width >>> 16);
        b[18] = (byte) (width >>> 8);
        b[19] = (byte) width;
        b[20] = (byte) (height >>> 24);
        b[21] = (byte) (height >>> 16);
        b[22] = (byte) (height >>> 8);
        b[23] = (byte) height;
        return b;
    }

    private static byte[] wav() {
        byte[] b = new byte[44 + 800];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WAVEfmt ".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 8);
        System.arraycopy("data".getBytes(StandardCharsets.US_ASCII), 0, b, 36, 4);
        for (int i = 44; i < b.length; i++) {
            b[i] = (byte) (i * 7);
        }
        return b;
    }

    // ------------------------------------------------------------------ accounts and HTTP

    private record Staff(UUID id, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "media-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\""
                + PASSWORD + "\"}", null), 200).get("accessToken").asString());
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"media-parent-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"" + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}",
                null), 201).get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> upload(String path, byte[] content, String filename, String bearer) throws Exception {
        String boundary = "bk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())), bearer);
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
