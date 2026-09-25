-- Battle 5C: learner_objective_states is recomputed from the skills that actually have
-- evidence (MasteryEngine#recalculateObjectiveState / LearnerObjectiveState#applyEvidenceRollup).
--
-- Before: mastery_probability averaged EVERY mapped skill, counting unpractised skills
-- as 0, so perfect evidence on 1 of 4 skills read as 0.25 ("still developing"): "not
-- assessed" became "failed". And status only left NOT_STARTED when that average was
-- above 0, so a child whose every observed answer was wrong stayed NOT_STARTED and was
-- invisible to Parent Insight: "failed" became "not assessed".
--
-- After:  mastery_probability = mean mastery over mapped skills WITH evidence;
--         status = MASTERED only if every mapped skill has evidence and that mean is at
--         the configured threshold, otherwise IN_PROGRESS once any skill has evidence.
-- Objectives with no evidenced skill are left untouched (they carry no evidence).

UPDATE learner_objective_states o
   SET mastery_probability = r.observed,
       status = CASE WHEN r.observed_skills = r.mapped_skills
                      AND r.observed >= ${objective_mastery_threshold}::numeric THEN 'MASTERED'
                     ELSE 'IN_PROGRESS' END,
       version = o.version + 1
  FROM (SELECT os.id,
               COUNT(m.skill_id) AS mapped_skills,
               COUNT(s.id) FILTER (WHERE s.evidence_count > 0) AS observed_skills,
               ROUND(AVG(s.mastery_probability) FILTER (WHERE s.evidence_count > 0), 4) AS observed
          FROM learner_objective_states os
          JOIN learning_objective_skills m ON m.learning_objective_id = os.learning_objective_id
          LEFT JOIN learner_skill_states s ON s.child_id = os.child_id AND s.skill_id = m.skill_id
         GROUP BY os.id) r
 WHERE r.id = o.id
   AND r.observed_skills > 0;
