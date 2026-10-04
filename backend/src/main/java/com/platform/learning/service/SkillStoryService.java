package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.learner.entity.LearnerSkillHistory;
import com.platform.learner.repository.LearnerSkillHistoryRepository;
import com.platform.learner.service.EvidenceStandard;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learning.service.SkillProgressService.CurriculumProgress;
import com.platform.learning.service.SkillProgressService.ObjectiveProgress;
import com.platform.learning.service.SkillProgressService.SkillProgress;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One skill, explained to a parent (X19): where it stands and on what evidence, each activity that
 * gave evidence on it with how many of its answers on the skill were right and where the skill stood
 * afterwards, and what is waiting on the child's path for it and why. Everything is the child's own
 * record, read with the same {@link EvidenceStandard} as every other standing; nothing is inferred.
 * Callers check the family first; a skill outside the child's curricula reads as not found.
 */
@Service
public class SkillStoryService {

    /** How many of the most recent activities on the skill are told. */
    static final int RECENT_ACTIVITIES = 20;

    /** One activity's answers on the skill, and the standing after them. */
    public record ActivityOnSkill(UUID attemptId, String activityTitle, Instant at, int answers, int correct,
                                  SkillStanding standingAfter, int evidenceAfter) {
    }

    /** A step waiting on the child's path that practises the skill, and who put it there. */
    public record WaitingStep(UUID learningPathItemId, String activityTitle, String status, String source, Instant addedAt,
                              String curriculumName) {
    }

    public record SkillStory(UUID skillId, String name, String description, String objectiveTitle, String curriculumName,
                             SkillProgress now, List<ActivityOnSkill> activities, int activitiesInAll, List<WaitingStep> waiting) {
    }

    private final SkillProgressService skillProgress;
    private final LearnerSkillHistoryRepository history;
    private final EvidenceStandard evidenceStandard;
    private final NamedParameterJdbcTemplate jdbc;

    public SkillStoryService(SkillProgressService skillProgress, LearnerSkillHistoryRepository history,
                             EvidenceStandard evidenceStandard, NamedParameterJdbcTemplate jdbc) {
        this.skillProgress = skillProgress;
        this.history = history;
        this.evidenceStandard = evidenceStandard;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SkillStory story(UUID childId, UUID skillId) {
        SkillProgress now = null;
        String objectiveTitle = null;
        String curriculumName = null;
        for (CurriculumProgress curriculum : skillProgress.progressFor(childId)) {
            for (ObjectiveProgress objective : curriculum.objectives()) {
                for (SkillProgress skill : objective.skills()) {
                    if (skill.skillId().equals(skillId) && now == null) {
                        now = skill;
                        objectiveTitle = objective.title();
                        curriculumName = curriculum.curriculum().name();
                    }
                }
            }
        }
        if (now == null) {
            throw new ResourceNotFoundException("Skill", skillId);
        }

        MapSqlParameterSource params = new MapSqlParameterSource("child", childId).addValue("skill", skillId)
                .addValue("limit", RECENT_ACTIVITIES);
        record Tally(UUID attemptId, String title, Instant at, int answers, int correct) {
        }
        List<Tally> tallies = jdbc.query("""
                SELECT a.id, act.title, COALESCE(a.completed_at, max(r.occurred_at)) AS at,
                       count(*) AS answers, count(*) FILTER (WHERE r.is_correct) AS correct
                FROM answer_records r
                JOIN question_version_skills qs ON qs.question_version_id = r.question_version_id AND qs.skill_id = :skill
                JOIN activity_attempts a ON a.id = r.activity_attempt_id AND a.child_id = :child
                JOIN activity_versions av ON av.id = a.activity_version_id
                JOIN activities act ON act.id = av.activity_id
                GROUP BY a.id, act.title, a.completed_at
                ORDER BY at DESC, a.id DESC
                LIMIT :limit
                """, params, (rs, i) -> new Tally(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getTimestamp("at").toInstant(), rs.getInt("answers"), rs.getInt("correct")));
        Integer inAll = jdbc.queryForObject("""
                SELECT count(DISTINCT a.id) FROM answer_records r
                JOIN question_version_skills qs ON qs.question_version_id = r.question_version_id AND qs.skill_id = :skill
                JOIN activity_attempts a ON a.id = r.activity_attempt_id AND a.child_id = :child
                """, params, Integer.class);

        // Where the skill stood after each activity: the evidence history replayed, as StandingHistory does.
        List<LearnerSkillHistory> rows = new ArrayList<>(history.findByChildIdAndSkillIdOrderByOccurredAtDesc(childId, skillId));
        java.util.Collections.reverse(rows);
        Map<UUID, int[]> evidenceAfter = new HashMap<>();
        Map<UUID, BigDecimal> masteryAfter = new HashMap<>();
        int evidence = 0;
        for (LearnerSkillHistory row : rows) {
            if (row.getTriggeredByType() != null) {
                evidence++;
            }
            if (row.getTriggeredById() != null) {
                evidenceAfter.put(row.getTriggeredById(), new int[]{evidence});
                masteryAfter.put(row.getTriggeredById(), row.getMasteryProbability());
            }
        }
        List<ActivityOnSkill> activities = tallies.stream().map(t -> {
            int[] after = evidenceAfter.get(t.attemptId());
            SkillStanding standing = after == null ? null : evidenceStandard.standingFor(after[0], masteryAfter.get(t.attemptId()));
            return new ActivityOnSkill(t.attemptId(), t.title(), t.at(), t.answers(), t.correct(), standing, after == null ? 0 : after[0]);
        }).toList();

        List<WaitingStep> waiting = jdbc.query("""
                SELECT i.id, act.title, i.status, i.source, i.added_at, cu.name AS curriculum
                FROM learning_path_items i
                JOIN learning_paths p ON p.id = i.learning_path_id
                JOIN enrollments e ON e.id = p.enrollment_id AND e.child_id = :child
                JOIN curriculum_versions cv ON cv.id = e.curriculum_version_id
                JOIN curriculums cu ON cu.id = cv.curriculum_id
                JOIN activity_version_skills s ON s.activity_version_id = i.activity_version_id AND s.skill_id = :skill
                JOIN activity_versions av ON av.id = i.activity_version_id
                JOIN activities act ON act.id = av.activity_id
                WHERE i.status IN ('PENDING', 'IN_PROGRESS')
                ORDER BY e.enrolled_at, i.sequence_order
                """, params, (rs, i) -> new WaitingStep(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("status"),
                rs.getString("source"), toInstant(rs.getTimestamp("added_at")), rs.getString("curriculum")));

        return new SkillStory(skillId, now.name(), now.description(), objectiveTitle, curriculumName, now, activities,
                inAll == null ? 0 : inAll, waiting);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

}
