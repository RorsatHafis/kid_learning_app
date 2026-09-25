-- Battle 5B: learner_skill_states.mastery_probability changes meaning.
--
-- Before: an EWMA started at 0 - after n answers, (1 - lr)^n of the value was that
-- starting 0 (an assumption), not evidence. A child with 2/2 correct read as 0.36.
-- After:  the bias-corrected EWMA - exactly the recency-weighted mean of the answers
-- actually observed (see MasteryEngine's class javadoc).
--
-- The conversion is exact, not an approximation: an EWMA started at 0 after n
-- observations equals the normalized weighted mean multiplied by (1 - (1 - lr)^n).
-- Dividing that factor back out yields the value MasteryEngine now maintains, so
-- existing learners continue on the same scale as new ones.
--
-- ${mastery_learning_rate} and ${objective_mastery_threshold} are the application's
-- own platform.mastery.* settings (see spring.flyway.placeholders in application.yaml),
-- so the conversion uses the rate the stored values were actually computed with.
--
-- learner_skill_history is append-only (trigger-enforced) and is intentionally NOT
-- rewritten: snapshots recorded before this migration keep the old meaning.

UPDATE learner_skill_states
   SET mastery_probability = ROUND(LEAST(1, mastery_probability
           / (1 - POWER(1 - ${mastery_learning_rate}::numeric, evidence_count::numeric))), 4),
       version = version + 1
 WHERE evidence_count > 0;

-- Objective rollups are averages over every mapped skill (an unassessed skill counts
-- as 0), recomputed exactly as MasteryEngine#recalculateObjectiveState and
-- LearnerObjectiveState#updateMastery do (status left unchanged at an average of 0).
UPDATE learner_objective_states o
   SET mastery_probability = r.average,
       status = CASE WHEN r.average >= ${objective_mastery_threshold}::numeric THEN 'MASTERED'
                     WHEN r.average > 0 THEN 'IN_PROGRESS'
                     ELSE o.status END,
       version = o.version + 1
  FROM (SELECT os.id, ROUND(AVG(COALESCE(s.mastery_probability, 0)), 4) AS average
          FROM learner_objective_states os
          JOIN learning_objective_skills m ON m.learning_objective_id = os.learning_objective_id
          LEFT JOIN learner_skill_states s ON s.child_id = os.child_id AND s.skill_id = m.skill_id
         GROUP BY os.id) r
 WHERE r.id = o.id;

-- Smart Review flags raised from the old values: retire every ACTIVE flag the converted
-- evidence no longer supports, applying SmartReviewService#evaluateAndSchedule's own rule
-- (needs review = evidence_count >= 2 AND mastery < 0.5) - the same outcome that method
-- would reach on the skill's next evidence. Already-scheduled review activities on
-- learning paths are left in place; they are ordinary scheduled work, not claims.
UPDATE review_items r
   SET status = 'RETIRED',
       version = r.version + 1
  FROM learner_skill_states s
 WHERE r.status = 'ACTIVE'
   AND s.child_id = r.child_id
   AND s.skill_id = r.skill_id
   AND NOT (s.evidence_count >= 2 AND s.mastery_probability < 0.5);
