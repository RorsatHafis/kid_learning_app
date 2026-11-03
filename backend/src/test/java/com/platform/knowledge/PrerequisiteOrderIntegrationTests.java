package com.platform.knowledge;

import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.knowledge.entity.SkillPrerequisite;
import com.platform.knowledge.repository.SkillPrerequisiteRepository;
import com.platform.knowledge.service.SkillPrerequisiteService;
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
 * Finding AE-1 (docs/ADAPTIVE-ENGINE.md): a skill's prerequisites come back in the order they were recorded, so the
 * "first prerequisite" reinforcement targets is the same on every run - not whatever order the database returns.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class PrerequisiteOrderIntegrationTests {

    @Autowired private SubjectRepository subjectRepository;
    @Autowired private SkillService skillService;
    @Autowired private SkillPrerequisiteService prerequisiteService;
    @Autowired private SkillPrerequisiteRepository prerequisiteRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void prerequisitesComeInTheOrderTheyWereRecorded() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("PRQ_" + suffix, "Prerequisites " + suffix, null)).getId();
        UUID skill = skillService.createSkill(subjectId, "PRQ_TOP_" + suffix, "Add to 20", null).getId();
        UUID[] prerequisites = new UUID[3];
        for (int i = 0; i < prerequisites.length; i++) {
            prerequisites[i] = skillService.createSkill(subjectId, "PRQ_" + i + "_" + suffix, "Before " + i, null).getId();
        }
        // Stored 0, 1, 2 but recorded (dated) 1, 0, 2 - so the table's own order is not the answer.
        int[] minutesAgo = {5, 10, 1};
        for (int i = 0; i < prerequisites.length; i++) {
            UUID link = prerequisiteService.addPrerequisite(skill, prerequisites[i]).getId();
            jdbc.update("update skill_prerequisites set created_at = now() - make_interval(mins => ?) where id = ?", minutesAgo[i], link);
        }

        for (int run = 0; run < 3; run++) {
            List<UUID> order = prerequisiteRepository.findBySkillId(skill).stream().map(SkillPrerequisite::getPrerequisiteSkillId).toList();
            assertThat(order).containsExactly(prerequisites[1], prerequisites[0], prerequisites[2]);
        }
    }
}
