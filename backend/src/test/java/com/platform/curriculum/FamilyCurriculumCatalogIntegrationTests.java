package com.platform.curriculum;

import com.platform.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * The family catalog for one child (X8): only that child's family may read it, it marks the
 * curricula for the child's age group with the rule automatic enrolment uses, lists those first,
 * and costs the same number of statements however large the catalog grows.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
/**
 * The curriculum catalogue for a child lists their age group first, only their family can read it, and it costs
 * the same number of queries however many curricula exist.
 */
@Tag("integration")
class FamilyCurriculumCatalogIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Catalog-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aChildsCatalogMarksTheirAgeGroupFirstAndOnlyTheirFamilyReadsIt() throws Exception {
        String parent = registerParent();
        String childId = createChild(parent, 6);

        JsonNode choices = json(get("/api/v1/children/" + childId + "/curricula", parent), 200);
        assertThat(choices.size()).isPositive();
        // The same rule as automatic enrolment: the first age group (display order) including age 6.
        UUID expectedHub = jdbc.queryForObject("select id from age_hubs where min_age <= 6 and (max_age is null or max_age >= 6) "
                + "order by display_order limit 1", UUID.class);
        String expectedCode = jdbc.queryForObject("select code from age_hubs where id = ?", String.class, expectedHub);
        boolean seenOther = false;
        for (JsonNode choice : choices) {
            assertThat(choice.get("forChildAge").asBoolean()).as(choice.toString())
                    .isEqualTo(choice.get("ageHubCode").asString().equals(expectedCode));
            // For-this-age first: once another age group appears, none of the child's follows.
            if (!choice.get("forChildAge").asBoolean()) {
                seenOther = true;
            } else {
                assertThat(seenOther).as("a curriculum for the child's age after other ages").isFalse();
            }
        }
        // The same curricula as the general catalog, nothing more (platform-owned, published).
        assertThat(choices.size()).isEqualTo(json(get("/api/v1/curricula", parent), 200).size());

        String stranger = registerParent();
        assertThat(get("/api/v1/children/" + childId + "/curricula", stranger).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/children/" + UUID.randomUUID() + "/curricula", parent).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/children/" + childId + "/curricula", null).statusCode()).isEqualTo(401);
    }

    @Test
    void theCatalogCostsTheSameStatementsHoweverManyCurriculaThereAre() throws Exception {
        String parent = registerParent();
        String childId = createChild(parent, 6);
        long generalBefore = statements("/api/v1/curricula", parent);
        long childBefore = statements("/api/v1/children/" + childId + "/curricula", parent);
        // A measurement of nothing proves nothing: the counter must see the listing's own queries.
        assertThat(generalBefore).isPositive();
        assertThat(childBefore).isPositive();

        // Twelve more published platform curricula, in an age group no test child has (90-99), so
        // nothing else's automatic enrolment changes; removed again afterwards.
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subject = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        List<UUID> curricula = new ArrayList<>();
        jdbc.update("insert into subjects (id, code, name) values (?, ?, ?)", subject, "CATALOG_" + suffix, "Catalog " + suffix);
        jdbc.update("insert into age_hubs (id, code, name, min_age, max_age, display_order) values (?, ?, ?, 90, 99, 99)",
                hub, "CATALOG_" + suffix, "Catalog " + suffix);
        try {
            for (int i = 0; i < 12; i++) {
                UUID curriculum = UUID.randomUUID();
                curricula.add(curriculum);
                jdbc.update("insert into curriculums (id, subject_id, age_hub_id, name) values (?, ?, ?, ?)",
                        curriculum, subject, hub, "Catalog " + suffix + " " + i);
                jdbc.update("insert into curriculum_versions (id, curriculum_id, version_number, status, published_at) "
                        + "values (?, ?, 1, 'PUBLISHED', now())", UUID.randomUUID(), curriculum);
            }
            long generalAfter = statements("/api/v1/curricula", parent);
            long childAfter = statements("/api/v1/children/" + childId + "/curricula", parent);
            System.out.println("Catalog statements before/after 12 more curricula: general " + generalBefore + " -> " + generalAfter
                    + ", child " + childBefore + " -> " + childAfter);
            assertThat(generalAfter).isEqualTo(generalBefore);
            assertThat(childAfter).isEqualTo(childBefore);
        } finally {
            for (UUID curriculum : curricula) {
                jdbc.update("delete from curriculum_versions where curriculum_id = ?", curriculum);
                jdbc.update("delete from curriculums where id = ?", curriculum);
            }
            jdbc.update("delete from age_hubs where id = ?", hub);
            jdbc.update("delete from subjects where id = ?", subject);
        }
    }

    // ------------------------------------------------------------------ helpers

    private long statements(String path, String token) throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        json(get(path, token), 200);
        return stats.getPrepareStatementCount();
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"catalog-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String createChild(String token, int age) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"Catalog Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(age).minusDays(10) + "\"}", token), 201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
}
