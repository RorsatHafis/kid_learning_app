package com.platform.knowledge;

import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finding PP-1 (docs/ADAPTIVE-ENGINE.md): an objective's skills come back in the order they were linked, so the steps
 * within an objective on a starting path (and in the journey builder) are in a stated order, not the database's.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class ObjectiveSkillOrderIntegrationTests {

    @Autowired private SubjectRepository subjectRepository;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveService objectiveService;
    @Autowired private LearningObjectiveSkillService objectiveSkillService;
    @Autowired private LearningObjectiveSkillRepository objectiveSkillRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anObjectivesSkillsComeInTheOrderTheyWereLinked() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("OSO_" + suffix, "Objective order " + suffix, null)).getId();
        LearningObjective objective = objectiveService.create(subjectId, "OSO_OBJ_" + suffix, "Add within 10", null);
        UUID[] skills = new UUID[3];
        int[] minutesAgo = {5, 10, 1};
        for (int i = 0; i < skills.length; i++) {
            skills[i] = skillService.createSkill(subjectId, "OSO_" + i + "_" + suffix, "Skill " + i, null).getId();
            objectiveSkillService.link(objective.getId(), skills[i]);
        }
        // Stored 0, 1, 2 but linked (dated) 1, 0, 2 - so the table's own order is not the answer.
        for (int i = 0; i < skills.length; i++) {
            jdbc.update("update learning_objective_skills set created_at = now() - make_interval(mins => ?)"
                    + " where learning_objective_id = ? and skill_id = ?", minutesAgo[i], objective.getId(), skills[i]);
        }

        for (int run = 0; run < 3; run++) {
            List<UUID> order = objectiveSkillRepository.findByLearningObjectiveId(objective.getId()).stream()
                    .map(LearningObjectiveSkill::getSkillId).toList();
            assertThat(order).containsExactly(skills[1], skills[0], skills[2]);
        }
    }
}
