package com.platform.subscription;

import com.platform.TestcontainersConfiguration;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Y35 over real HTTP against PostgreSQL: a school's services need a confirmed, paid plan (Y44), and that plan
 * limits what the school adds (teachers, classes in use, children) and never removes what it already holds; storage is measured from what
 * an author keeps, limited by their allowance in the real upload path, and an administrator's
 * exception to it is audited. Each school, author and plan sees only its own.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SchoolPlanAndStorageIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "School-Plan-Test-Pass-2026";
    private static final long MB = 1024L * 1024L;

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    // ------------------------------------------------------------------ school plans

    @Test
    void anUnpaidSchoolHasNoPlanAdministratorsCantGiveOneAndPlansStayWithTheirAudience() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal);

        // Y44: a new school isn't paid; its billing stays open to its principal, its services don't.
        JsonNode own = json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200);
        assertThat(own.get("access").get("paid").asBoolean()).isFalse();
        assertThat(own.get("access").get("paymentPending").asBoolean()).isFalse();
        assertThat(own.get("plan").get("audience").asString()).isEqualTo("SCHOOL");
        assertThat(keys(own)).containsExactly("SCHOOL_CHILDREN_MAX", "SCHOOL_TEACHERS_MAX", "SCHOOL_CLASSES_MAX", "TEACHER_STORAGE_MB");
        JsonNode refused = json(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1A\"}", principal), 402);
        assertThat(refused.get("schoolPaymentRequired").asBoolean()).isTrue();
        assertThat(classCount(schoolId)).isZero();

        String schoolPlan = schoolPlan(admin, 1, 1, 1, 1);
        String familyPlan = familyPlan(admin);
        // A limit of the other audience can't be set on a plan.
        String draft = json(post("/api/v1/admin/plans", "{\"code\":\"S_" + suffix() + "\",\"name\":\"Draft\",\"audience\":\"SCHOOL\"}",
                admin), 201).get("plan").get("id").asString();
        assertThat(put("/api/v1/admin/plans/" + draft, "{\"name\":\"Draft\",\"selfService\":false,\"limits\":{\"CHILDREN_MAX\":1}}",
                admin).statusCode()).isEqualTo(400);
        // A plan is never given to a school without payment (Y44), nor invoiced across audiences.
        assertThat(json(post("/api/v1/admin/schools/" + schoolId + "/subscription", "{\"planId\":\"" + schoolPlan + "\"}", admin), 409)
                .get("detail").asString()).contains("invoice the school instead");
        assertThat(post("/api/v1/admin/schools/" + schoolId + "/invoices", "{\"planId\":\"" + familyPlan + "\",\"idempotencyKey\":\"f\"}",
                admin).statusCode()).isEqualTo(400);
        assertThat(json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access").get("paid").asBoolean()).isFalse();
        String parentEmail = "y35-parent-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(parentEmail);
        String familyId = json(get("/api/v1/admin/families?parentEmail=" + parentEmail, admin), 200).get("familyId").asString();
        assertThat(post("/api/v1/admin/families/" + familyId + "/subscription", "{\"planId\":\"" + schoolPlan + "\"}", admin)
                .statusCode()).isEqualTo(400);
        // A parent's own plan choices never include school plans.
        for (JsonNode choice : json(get("/api/v1/family/plan", new User(null, null, parent)), 200).get("choices")) {
            assertThat(choice.get("plan").get("audience").asString()).isEqualTo("FAMILY");
        }

        // Only the school's principal (or an administrator) sees its plan; only an administrator invoices it.
        User otherPrincipal = user(PlatformRole.PRINCIPAL);
        User teacher = user(PlatformRole.TEACHER);
        assertThat(get("/api/v1/schools/" + schoolId + "/plan", otherPrincipal).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/schools/" + schoolId + "/plan", teacher).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/schools/" + schoolId + "/plan", new User(null, null, parent)).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/admin/schools/" + schoolId + "/invoices", "{\"planId\":\"" + schoolPlan + "\",\"idempotencyKey\":\"p\"}",
                principal).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/admin/schools/" + schoolId + "/subscription", principal).statusCode()).isEqualTo(403);
    }

    @Test
    void aPaidSchoolPlanLimitsWhatTheSchoolAddsAndARefundStopsServicesButRemovesNothing() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal);
        String plan = schoolPlan(admin, 1, 1, 1, 50);
        String paid = pay(admin, schoolId, plan);

        JsonNode own = json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200);
        assertThat(own.get("access").get("paid").asBoolean()).isTrue();
        assertThat(own.get("onDefaultPlan").asBoolean()).isFalse();
        assertThat(own.get("subscription").has("note")).as("the operator's note is not the principal's").isFalse();
        String classA = createClass(principal, schoolId, "Grade 1A");
        assertThat(entitlement(json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200), "SCHOOL_CLASSES_MAX")
                .get("used").asInt()).isEqualTo(1);

        // Classes: one in use; a second is refused, and nothing is created.
        JsonNode refused = json(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1B\"}", principal), 409);
        assertThat(refused.get("title").asString()).isEqualTo("Plan Limit Reached");
        assertThat(refused.get("entitlement").asString()).isEqualTo("SCHOOL_CLASSES_MAX");
        assertThat(classCount(schoolId)).isEqualTo(1);
        // Archived, it frees its place; a class brought back takes one again.
        assertThat(post("/api/v1/schools/classes/" + classA + "/archive", "", principal).statusCode()).isEqualTo(200);
        String classB = createClass(principal, schoolId, "Grade 1B");
        assertThat(json(post("/api/v1/schools/classes/" + classA + "/restore", "", principal), 409).get("entitlement").asString())
                .isEqualTo("SCHOOL_CLASSES_MAX");

        // Teachers: one place; inviting someone already invited takes none.
        User first = user(PlatformRole.TEACHER);
        User secondTeacher = user(PlatformRole.TEACHER);
        assertThat(invite(principal, schoolId, first).statusCode()).isEqualTo(201);
        assertThat(json(invite(principal, schoolId, secondTeacher), 409).get("entitlement").asString()).isEqualTo("SCHOOL_TEACHERS_MAX");
        assertThat(jdbc.queryForObject("select count(*) from school_staff where school_id = ?::uuid", Integer.class, schoolId)).isEqualTo(1);
        assertThat(invite(principal, schoolId, first).body()).as("already invited: no new place").doesNotContain("Plan Limit Reached");

        // Children: one place; the same child joining another class of the school takes none.
        String code = joinCode(principal, classB);
        Family one = familyWithChild();
        Family two = familyWithChild();
        assertThat(join(one, code).statusCode()).isEqualTo(200);
        assertThat(join(one, code).statusCode()).as("joining again").isEqualTo(200);
        assertThat(json(join(two, code), 409).get("entitlement").asString()).isEqualTo("SCHOOL_CHILDREN_MAX");
        assertThat(access(schoolId, two.childId())).isZero();

        // Refunded: the school's services stop at once - for its principal, its teachers and families joining -
        // and everything it holds stays.
        json(post("/api/v1/admin/payments/" + paid + "/refund", "{\"reason\":\"Refund agreed\"}", admin), 200);
        assertThat(json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access").get("paid").asBoolean()).isFalse();
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 2\"}", principal).statusCode()).isEqualTo(402);
        assertThat(get("/api/v1/schools/classes/" + classB + "/children", principal).statusCode()).isEqualTo(402);
        assertThat(join(two, code).statusCode()).isEqualTo(402);
        assertThat(access(schoolId, one.childId())).isEqualTo(1);
        assertThat(classCount(schoolId)).isEqualTo(2);

        // Paid again on a roomier plan: everything is back, and the school can grow.
        pay(admin, schoolId, schoolPlan(admin, 10, 10, 10, 50));
        assertThat(join(two, code).statusCode()).isEqualTo(200);
        assertThat(invite(principal, schoolId, secondTeacher).statusCode()).isEqualTo(201);
        assertThat(post("/api/v1/schools/classes/" + classA + "/restore", "", principal).statusCode()).isEqualTo(200);

        // Paid on the smaller plan again: lower limits never remove anything; they only stop new additions.
        pay(admin, schoolId, plan);
        JsonNode over = json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200);
        assertThat(entitlement(over, "SCHOOL_CHILDREN_MAX").get("used").asInt()).isEqualTo(2);
        assertThat(entitlement(over, "SCHOOL_CLASSES_MAX").get("used").asInt()).isEqualTo(2);
        assertThat(access(schoolId, two.childId())).isEqualTo(1);
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 3\"}", principal).statusCode()).isEqualTo(409);

        // Each confirmation is the administrator's, audited with the receipt.
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PAYMENT_CONFIRMED' and actor_id = ?::uuid",
                Integer.class, admin.id())).isEqualTo(3);
    }

    // ------------------------------------------------------------------ storage

    @Test
    void storageIsMeasuredFromWhatAnAuthorKeepsAndTheUploadIsRefusedPastTheAllowance() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User teacher = user(PlatformRole.TEACHER);

        // A teacher in no school: the school default plan's allowance.
        JsonNode start = json(get("/api/v1/studio/storage", teacher), 200);
        assertThat(start.get("usedBytes").asLong()).isZero();
        assertThat(start.get("allowance").get("source").asString()).isEqualTo("DEFAULT_PLAN");

        // An exception of 0 MB: nothing more fits, and nothing is stored.
        json(put("/api/v1/admin/accounts/" + teacher.id() + "/storage-allowance", "{\"limitMb\":0,\"note\":\"Testing a full space\"}",
                admin), 200);
        JsonNode full = json(upload(noise(40), "small.png", teacher), 409);
        assertThat(full.get("title").asString()).isEqualTo("Storage Limit Reached");
        assertThat(full.get("limitBytes").asLong()).isZero();
        assertThat(assetCount(teacher)).isZero();

        // 1 MB: a file of about 0.7 MB fits; a second doesn't - until the first is deleted.
        json(put("/api/v1/admin/accounts/" + teacher.id() + "/storage-allowance", "{\"limitMb\":1,\"note\":\"One MB\"}", admin), 200);
        byte[] big = noise(480);
        assertThat(big.length).isBetween(600_000, 1_000_000);
        JsonNode kept = json(upload(big, "big.png", teacher), 201);
        JsonNode refused = json(upload(noise(480), "another.png", teacher), 409);
        assertThat(refused.get("usedBytes").asLong()).isEqualTo(big.length);
        // The same file again takes no more space, so it is never refused.
        assertThat(upload(big, "big again.png", teacher).statusCode()).isEqualTo(201);
        assertThat(assetCount(teacher)).isEqualTo(1);

        JsonNode view = json(get("/api/v1/studio/storage", teacher), 200);
        assertThat(view.get("usedBytes").asLong()).isEqualTo(big.length);
        assertThat(view.get("allowance").get("source").asString()).isEqualTo("OVERRIDE");
        assertThat(view.get("allowance").get("limitBytes").asLong()).isEqualTo(MB);
        assertThat(view.get("state").asString()).isEqualTo("OK");
        assertThat(view.get("largest").get(0).get("id").asString()).isEqualTo(kept.get("id").asString());
        assertThat(view.get("slices").get(0).get("unused").asBoolean()).isTrue();

        assertThat(delete("/api/v1/studio/media/" + kept.get("id").asString(), teacher).statusCode()).isEqualTo(204);
        assertThat(json(get("/api/v1/studio/storage", teacher), 200).get("usedBytes").asLong()).isZero();
        assertThat(upload(noise(480), "fits now.png", teacher).statusCode()).isEqualTo(201);

        // Lowered below what is kept: everything stays and can be used; only new uploads wait.
        json(put("/api/v1/admin/accounts/" + teacher.id() + "/storage-allowance", "{\"limitMb\":0,\"note\":\"Lowered\"}", admin), 200);
        JsonNode over = json(get("/api/v1/studio/storage", teacher), 200);
        assertThat(over.get("state").asString()).isEqualTo("OVER");
        assertThat(assetCount(teacher)).isEqualTo(1);
        assertThat(get("/api/v1/studio/media", teacher).statusCode()).isEqualTo(200);

        // Cleared: back to what the plans give; every change is in the audit history with its note.
        json(post("/api/v1/admin/accounts/" + teacher.id() + "/storage-allowance/clear", "{\"note\":\"Back to the plan\"}", admin), 200);
        assertThat(json(get("/api/v1/studio/storage", teacher), 200).get("allowance").get("source").asString()).isEqualTo("DEFAULT_PLAN");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where resource_id = ?::uuid and action in "
                + "('STORAGE_ALLOWANCE_SET', 'STORAGE_ALLOWANCE_CLEARED')", Integer.class, teacher.id())).isEqualTo(4);
        assertThat(put("/api/v1/admin/accounts/" + teacher.id() + "/storage-allowance", "{\"limitMb\":5,\"note\":\" \"}", admin)
                .statusCode()).as("a note is required").isEqualTo(400);
    }

    @Test
    void twoUploadsAtOnceCantBothTakeTheLastFreeSpace() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User teacher = user(PlatformRole.TEACHER);
        json(put("/api/v1/admin/accounts/" + teacher.id() + "/storage-allowance", "{\"limitMb\":1,\"note\":\"Race\"}", admin), 200);

        for (int round = 0; round < 3; round++) {
            jdbc.update("delete from content_assets where owner_account_id = ?::uuid", teacher.id());
            CompletableFuture<HttpResponse<String>> a = http.sendAsync(uploadRequest(noise(480), "a.png", teacher),
                    HttpResponse.BodyHandlers.ofString());
            CompletableFuture<HttpResponse<String>> b = http.sendAsync(uploadRequest(noise(480), "b.png", teacher),
                    HttpResponse.BodyHandlers.ofString());
            List<Integer> statuses = List.of(a.get().statusCode(), b.get().statusCode());
            assertThat(statuses).as("round " + round).containsExactlyInAnyOrder(201, 409);
            assertThat(assetCount(teacher)).isEqualTo(1);
        }
    }

    @Test
    void aTeachersAllowanceComesFromTheirSchoolsPlanAndEachAuthorSeesOnlyTheirOwn() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User principal = user(PlatformRole.PRINCIPAL);
        User teacher = user(PlatformRole.TEACHER);
        String schoolId = createSchool(principal);
        pay(admin, schoolId, schoolPlan(admin, 10, 10, 10, 3));
        assertThat(invite(principal, schoolId, teacher).statusCode()).isEqualTo(201);
        for (JsonNode invitation : json(get("/api/v1/schools/invitations/mine", teacher), 200)) {
            post("/api/v1/schools/invitations/" + invitation.get("id").asString() + "/accept", "", teacher);
        }

        JsonNode storage = json(get("/api/v1/studio/storage", teacher), 200);
        assertThat(storage.get("allowance").get("source").asString()).isEqualTo("SCHOOL_PLAN");
        assertThat(storage.get("allowance").get("limitMb").asInt()).isEqualTo(3);
        JsonNode principalStorage = json(get("/api/v1/studio/storage", principal), 200);
        assertThat(principalStorage.get("allowance").get("limitMb").asInt()).as("the principal works in the school too").isEqualTo(3);

        // The platform's own media counts against no one.
        JsonNode platform = json(get("/api/v1/studio/storage", admin), 200);
        assertThat(platform.get("allowance").get("source").asString()).isEqualTo("PLATFORM");
        assertThat(platform.get("allowance").get("limitMb").isNull()).isTrue();

        // One teacher's upload is never counted against another.
        User other = user(PlatformRole.TEACHER);
        json(upload(noise(60), "mine.png", teacher), 201);
        assertThat(json(get("/api/v1/studio/storage", other), 200).get("usedBytes").asLong()).isZero();

        // The overview is the administrator's alone; it lists this teacher with their usage.
        assertThat(get("/api/v1/admin/storage", teacher).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/admin/accounts/" + other.id() + "/storage", teacher).statusCode()).isEqualTo(403);
        assertThat(put("/api/v1/admin/accounts/" + teacher.id() + "/storage-allowance", "{\"limitMb\":100,\"note\":\"Mine\"}", teacher)
                .statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/studio/storage", new User(null, null, registerParent())).statusCode()).isEqualTo(403);
        JsonNode overview = json(get("/api/v1/admin/storage", admin), 200);
        JsonNode row = null;
        for (JsonNode owner : overview.get("owners")) {
            if (owner.get("accountId").asString().equals(teacher.id())) {
                row = owner;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("files").asInt()).isEqualTo(1);
        assertThat(row.get("allowance").get("source").asString()).isEqualTo("SCHOOL_PLAN");
        assertThat(overview.get("storedBytes").asLong()).isPositive();
        // A parent keeps no media: no allowance to set.
        String parentEmail = "y35-parent-" + UUID.randomUUID() + "@example.com";
        registerParent(parentEmail);
        String parentId = jdbc.queryForObject("select id::text from accounts where normalized_email = ?", String.class, parentEmail);
        assertThat(put("/api/v1/admin/accounts/" + parentId + "/storage-allowance", "{\"limitMb\":1,\"note\":\"No\"}", admin)
                .statusCode()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ helpers

    private record User(String id, String email, String token) {
    }

    private record Family(String token, String childId) {
    }

    private User user(PlatformRole role) throws Exception {
        String email = "y35-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        // M8: an administrator's money and access actions need the password re-entered.
        if (role == PlatformRole.ADMIN) {
            com.platform.TestAdmin.stepUp(port, body.get("accessToken").asString(), PASSWORD);
        }
        return new User(body.get("accountId").asString(), email, body.get("accessToken").asString());
    }

    private String registerParent() throws Exception {
        return registerParent("y35-parent-" + UUID.randomUUID() + "@example.com");
    }

    private String registerParent(String email) throws Exception {
        String token = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        jdbc.update("update accounts set email_verified_at = now() where normalized_email = ?", email);
        return token;
    }

    private Family familyWithChild() throws Exception {
        String parent = registerParent();
        String child = json(post("/api/v1/children", "{\"displayName\":\"Plan Child\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\"}", new User(null, null, parent)), 201).get("id").asString();
        return new Family(parent, child);
    }

    private String createSchool(User principal) throws Exception {
        return json(post("/api/v1/schools", "{\"name\":\"Y35 School " + suffix() + "\"}", principal), 201).get("id").asString();
    }

    private String createClass(User principal, String schoolId, String name) throws Exception {
        return json(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"" + name + "\"}", principal), 201).get("id").asString();
    }

    private HttpResponse<String> invite(User principal, String schoolId, User teacher) throws Exception {
        return post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal);
    }

    private String joinCode(User principal, String classId) throws Exception {
        return json(post("/api/v1/schools/classes/" + classId + "/join-code", "", principal), 201).get("code").asString();
    }

    private HttpResponse<String> join(Family family, String code) throws Exception {
        return post("/api/v1/children/" + family.childId() + "/schools/join", "{\"code\":\"" + code + "\"}",
                new User(null, null, family.token()));
    }

    private String schoolPlan(User admin, int children, int teachers, int classes, int storageMb) throws Exception {
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"SCHOOL_" + suffix() + "\",\"name\":\"School test plan\","
                + "\"audience\":\"SCHOOL\"}", admin), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"School test plan\",\"selfService\":false,\"priceAmountMinor\":12000,"
                + "\"priceCurrency\":\"USD\",\"billingInterval\":\"YEAR\",\"limits\":{"
                + "\"SCHOOL_CHILDREN_MAX\":" + children + ",\"SCHOOL_TEACHERS_MAX\":" + teachers + ",\"SCHOOL_CLASSES_MAX\":" + classes
                + ",\"TEACHER_STORAGE_MB\":" + storageMb + "}}", admin), 200);
        JsonNode active = json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        assertThat(active.get("plan").get("audience").asString()).isEqualTo("SCHOOL");
        return planId;
    }

    /** Y44: a school is paid as a real one is - invoiced by an administrator, confirmed against the receipt. Returns the order. */
    private String pay(User admin, String schoolId, String planId) throws Exception {
        String orderId = json(post("/api/v1/admin/schools/" + schoolId + "/invoices", "{\"planId\":\"" + planId
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", admin), 201).get("id").asString();
        json(post("/api/v1/admin/payments/" + orderId + "/confirm", com.platform.TestAdmin.receipt(port, admin.token(), orderId,
                "BANK-" + suffix()), admin), 200);
        return orderId;
    }

    private String familyPlan(User admin) throws Exception {
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"FAMILY_" + suffix() + "\",\"name\":\"Family test plan\"}", admin),
                201).get("plan").get("id").asString();
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        return planId;
    }

    private static List<String> keys(JsonNode plan) {
        return plan.get("entitlements").valueStream().map(e -> e.get("key").asString()).toList();
    }

    private static JsonNode entitlement(JsonNode plan, String key) {
        return plan.get("entitlements").valueStream().filter(e -> e.get("key").asString().equals(key)).findFirst().orElseThrow();
    }

    private int classCount(String schoolId) {
        return jdbc.queryForObject("select count(*) from school_classes where school_id = ?::uuid", Integer.class, schoolId);
    }

    private int access(String schoolId, String childId) {
        return jdbc.queryForObject("select count(*) from school_child_access where school_id = ?::uuid and child_id = ?::uuid "
                + "and status = 'ACTIVE'", Integer.class, schoolId, childId);
    }

    private int assetCount(User owner) {
        return jdbc.queryForObject("select count(*) from content_assets where owner_account_id = ?::uuid", Integer.class, owner.id());
    }

    /** A picture of random pixels (it barely compresses), {@code side} pixels square; unique every call. */
    private static byte[] noise(int side) throws Exception {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpRequest uploadRequest(byte[] content, String filename, User author) throws Exception {
        String boundary = "bk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(uri("/api/v1/studio/media")).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + author.token())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
    }

    private HttpResponse<String> upload(byte[] content, String filename, User author) throws Exception {
        return http.send(uploadRequest(content, filename, author), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, User user) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), user);
    }

    private HttpResponse<String> post(String path, String body, User user) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), user);
    }

    private HttpResponse<String> put(String path, String body, User user) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), user);
    }

    private HttpResponse<String> delete(String path, User user) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).DELETE(), user);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, User user) throws Exception {
        if (user != null && user.token() != null) {
            request.header("Authorization", "Bearer " + user.token());
            com.platform.TestAdmin.withStepUp(request, user.token());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
