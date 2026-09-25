ALTER TABLE learner_skill_history
    DROP CONSTRAINT fk_learner_skill_history_attempt;

ALTER TABLE learner_skill_history
    RENAME COLUMN triggered_by_attempt_id TO triggered_by_id;

ALTER TABLE learner_skill_history
    ADD COLUMN triggered_by_type VARCHAR(20);

ALTER TABLE learner_skill_history
    ADD CONSTRAINT ck_learner_skill_history_triggered_by_type
    CHECK (triggered_by_type IS NULL OR triggered_by_type IN ('ACTIVITY_ATTEMPT', 'ASSESSMENT_ATTEMPT'));

ALTER TABLE learner_skill_history
    ADD CONSTRAINT ck_learner_skill_history_triggered_by_consistency
    CHECK ((triggered_by_id IS NULL) = (triggered_by_type IS NULL));
