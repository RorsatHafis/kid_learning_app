package com.platform.curriculum;

import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillService;
import com.platform.content.entity.QuestionType;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SUB-1 / FLEX-1: the public pages' subjects come from the catalog, as many as it has - a subject with a published
 * family curriculum is available with the ages it covers; an active subject without one is in preparation. Read by
 * anyone.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PublicSubjectsIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}") private int port;
    @Autowired private SubjectRepository subjects;
    @Autowired private AgeHubRepository ageHubs;
    @Autowired private LearningObjectiveService objectives;
    @Autowired private CurriculumService curricula;
    @Autowired private SkillService skills;
    @Autowired private LearningObjectiveSkillService objectiveSkills;
    @Autowired private QuestionService questions;
    @Autowired private ActivityService activities;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void eachSubjectInTheCatalogIsShownAvailableWithItsAgesOrInPreparation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Subject science = subjects.save(Subject.create("SCI_" + suffix, "Science " + suffix, "Plants and water"));
        Subject music = subjects.save(Subject.create("MUS_" + suffix, "Music " + suffix, null));
        AgeHub young = ageHubs.save(AgeHub.create("SCY_" + suffix, "Young " + suffix, 5, 7, 98));
        AgeHub older = ageHubs.save(AgeHub.create("SCO_" + suffix, "Older " + suffix, 8, 10, 99));
        publish(science, young, "Science - Young " + suffix);
        publish(science, older, "Science - Older " + suffix);

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/site/subjects")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("read by anyone").isEqualTo(200);
        JsonNode shown = JSON.readTree(response.body());

        JsonNode sci = find(shown, science.getCode());
        assertThat(sci.get("available").asBoolean()).isTrue();
        assertThat(sci.get("minAge").asInt()).isEqualTo(5);
        assertThat(sci.get("maxAge").asInt()).isEqualTo(10);
        assertThat(sci.get("description").asString()).isEqualTo("Plants and water");
        JsonNode mus = find(shown, music.getCode());
        assertThat(mus.get("available").asBoolean()).as("no published curriculum yet").isFalse();
        assertThat(mus.get("minAge").isNull()).isTrue();
    }

    private void publish(Subject subject, AgeHub hub, String name) {
        String key = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LearningObjective objective = objectives.create(subject.getId(), "OBJ_" + key, name, null);
        UUID skill = skills.createSkill(subject.getId(), "SK_" + key, "Skill " + key, null).getId();
        objectiveSkills.link(objective.getId(), skill);
        var question = questions.createDraftVersion(questions.createQuestion(subject.getId(), QuestionType.NUMERIC).getId(), "1 + 1 = ?", "2", 1);
        questions.tagSkill(question.getId(), skill);
        var published = questions.publish(question.getId());
        var activity = activities.createDraftVersion(activities.createActivity(subject.getId(), null, "PRACTICE", "Practice " + key).getId(), "Add", 1, 60);
        activities.tagSkill(activity.getId(), skill);
        activities.addItem(activity.getId(), published.getId(), 0, java.math.BigDecimal.ONE);
        activities.publish(activity.getId());
        Curriculum curriculum = curricula.createCurriculum(subject.getId(), hub.getId(), name);
        var version = curricula.createDraftVersion(curriculum.getId());
        curricula.addObjective(version.getId(), objective.getId(), 0);
        curricula.publish(version.getId());
    }

    private static JsonNode find(JsonNode list, String code) {
        for (JsonNode subject : list) {
            if (code.equals(subject.get("code").asString())) return subject;
        }
        throw new AssertionError("not shown: " + code);
    }
}
