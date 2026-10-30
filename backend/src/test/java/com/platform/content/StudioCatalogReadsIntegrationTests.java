package com.platform.content;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A subject's catalog in the Studio - skills with their prerequisites and Khmer, chapters with their skills and Khmer -
 * is read together: it takes the same number of statements with one skill as with many, and says the same things.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Tag("integration")
class StudioCatalogReadsIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Catalog-Reads-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theCatalogCostsTheSameReadsHoweverManySkillsItHas() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"CR_" + suffix + "\",\"name\":\"Reads " + suffix + "\"}",
                admin), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"CRO_" + suffix
                + "\",\"title\":\"Counting\",\"khmer\":{\"name\":\"ការរាប់\"}}", admin), 201).get("id").asString();
        List<String> skillIds = new ArrayList<>();
        skillIds.add(skill(admin, subjectId, suffix, 0));
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillIds.get(0), "", admin), 200);
        String catalog = "/api/v1/studio/subjects/" + subjectId + "/catalog";
        long withOne = statements(catalog, admin);

        // Seven more skills, each linked to the chapter, each after the one before, each named in Khmer.
        for (int i = 1; i < 8; i++) {
            skillIds.add(skill(admin, subjectId, suffix, i));
            json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillIds.get(i), "", admin), 200);
            json(post("/api/v1/studio/skills/" + skillIds.get(i) + "/prerequisites/" + skillIds.get(i - 1), "", admin), 200);
        }
        long withEight = statements(catalog, admin);
        System.out.println("SQL statements for the Studio catalog: 1 skill " + withOne + ", 8 skills " + withEight);
        assertThat(withEight).as("the catalog's reads don't grow with its skills (1 skill: %s)", withOne).isEqualTo(withOne);

        // ...and it says the same as before: prerequisites, the chapter's skills, every Khmer name.
        JsonNode read = json(get(catalog, admin), 200);
        assertThat(read.get("skills")).hasSize(8);
        for (JsonNode skill : read.get("skills")) {
            int i = Integer.parseInt(skill.get("name").asString().substring("Skill ".length()));
            assertThat(skill.get("khmer").get("name").asString()).isEqualTo("ជំនាញ " + i);
            List<String> prerequisites = new ArrayList<>();
            skill.get("prerequisiteSkillIds").forEach(p -> prerequisites.add(p.asString()));
            assertThat(prerequisites).isEqualTo(i == 0 ? List.of() : List.of(skillIds.get(i - 1)));
        }
        JsonNode objective = read.get("objectives").get(0);
        assertThat(objective.get("khmer").get("name").asString()).isEqualTo("ការរាប់");
        List<String> linked = new ArrayList<>();
        objective.get("skillIds").forEach(s -> linked.add(s.asString()));
        assertThat(linked).containsExactlyInAnyOrderElementsOf(skillIds);
    }

    private String skill(String admin, String subjectId, String suffix, int i) throws Exception {
        return json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"CRS" + i + "_" + suffix + "\",\"name\":\"Skill " + i
                + "\",\"khmer\":{\"name\":\"ជំនាញ " + i + "\"}}", admin), 201).get("id").asString();
    }

    private long statements(String path, String bearer) throws Exception {
        json(get(path, bearer), 200); // warm
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        json(get(path, bearer), 200);
        return stats.getPrepareStatementCount();
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "catalog-reads-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer);
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
