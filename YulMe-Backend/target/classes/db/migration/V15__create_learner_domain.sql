
CREATE TABLE learner_profiles (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    total_learning_time_seconds BIGINT NOT NULL DEFAULT 0,
    total_activities_completed INT NOT NULL DEFAULT 0,
    last_activity_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learner_profiles_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learner_profiles_child UNIQUE (child_id),
    CONSTRAINT ck_learner_profiles_total_learning_time CHECK (total_learning_time_seconds >= 0),
    CONSTRAINT ck_learner_profiles_total_activities CHECK (total_activities_completed >= 0),
    CONSTRAINT ck_learner_profiles_version CHECK (version >= 0)
);

CREATE INDEX ix_learner_profiles_child_id ON learner_profiles (child_id);

CREATE TRIGGER trg_learner_profiles_updated_at
    BEFORE UPDATE ON learner_profiles
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE learner_skill_states (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    mastery_probability NUMERIC(5, 4) NOT NULL DEFAULT 0,
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    evidence_count INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    incorrect_count INT NOT NULL DEFAULT 0,
    last_evidence_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learner_skill_states_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learner_skill_states_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learner_skill_states_child_skill UNIQUE (child_id, skill_id),
    CONSTRAINT ck_learner_skill_states_mastery CHECK (mastery_probability >= 0 AND mastery_probability <= 1),
    CONSTRAINT ck_learner_skill_states_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_learner_skill_states_evidence_count CHECK (evidence_count >= 0),
    CONSTRAINT ck_learner_skill_states_correct_count CHECK (correct_count >= 0),
    CONSTRAINT ck_learner_skill_states_incorrect_count CHECK (incorrect_count >= 0),
    CONSTRAINT ck_learner_skill_states_counts_consistent CHECK (correct_count + incorrect_count <= evidence_count),
    CONSTRAINT ck_learner_skill_states_version CHECK (version >= 0)
);

CREATE INDEX ix_learner_skill_states_child_id ON learner_skill_states (child_id);
CREATE INDEX ix_learner_skill_states_skill_id ON learner_skill_states (skill_id);

CREATE TRIGGER trg_learner_skill_states_updated_at
    BEFORE UPDATE ON learner_skill_states
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE learner_skill_history (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    mastery_probability NUMERIC(5, 4) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    triggered_by_attempt_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learner_skill_history_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learner_skill_history_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learner_skill_history_attempt
        FOREIGN KEY (triggered_by_attempt_id) REFERENCES activity_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_learner_skill_history_mastery CHECK (mastery_probability >= 0 AND mastery_probability <= 1),
    CONSTRAINT ck_learner_skill_history_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX ix_learner_skill_history_child_skill_occurred
    ON learner_skill_history (child_id, skill_id, occurred_at DESC);

CREATE TRIGGER trg_learner_skill_history_immutable
    BEFORE UPDATE OR DELETE ON learner_skill_history
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE learner_objective_states (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    learning_objective_id UUID NOT NULL,
    mastery_probability NUMERIC(5, 4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learner_objective_states_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learner_objective_states_objective
        FOREIGN KEY (learning_objective_id) REFERENCES learning_objectives (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learner_objective_states_child_objective UNIQUE (child_id, learning_objective_id),
    CONSTRAINT ck_learner_objective_states_mastery CHECK (mastery_probability >= 0 AND mastery_probability <= 1),
    CONSTRAINT ck_learner_objective_states_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'MASTERED')),
    CONSTRAINT ck_learner_objective_states_version CHECK (version >= 0)
);

CREATE INDEX ix_learner_objective_states_child_id ON learner_objective_states (child_id);
CREATE INDEX ix_learner_objective_states_objective_id ON learner_objective_states (learning_objective_id);

CREATE TRIGGER trg_learner_objective_states_updated_at
    BEFORE UPDATE ON learner_objective_states
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
