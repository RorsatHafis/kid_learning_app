
CREATE TABLE adaptation_rules (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    rule_type VARCHAR(30) NOT NULL,
    condition_definition JSONB NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_adaptation_rules_code UNIQUE (code),
    CONSTRAINT ck_adaptation_rules_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_adaptation_rules_type
        CHECK (rule_type IN ('REMEDIATION', 'ADVANCEMENT', 'PREREQUISITE', 'TARGETED_REVIEW')),
    CONSTRAINT ck_adaptation_rules_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_adaptation_rules_version CHECK (version >= 0)
);

CREATE TRIGGER trg_adaptation_rules_updated_at
    BEFORE UPDATE ON adaptation_rules
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE recommendation_requests (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    learning_path_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_recommendation_requests_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recommendation_requests_learning_path
        FOREIGN KEY (learning_path_id) REFERENCES learning_paths (id) ON DELETE RESTRICT
);

CREATE INDEX ix_recommendation_requests_child_id ON recommendation_requests (child_id);
CREATE INDEX ix_recommendation_requests_learning_path_id ON recommendation_requests (learning_path_id);

CREATE TRIGGER trg_recommendation_requests_immutable
    BEFORE UPDATE OR DELETE ON recommendation_requests
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE recommendations (
    id UUID PRIMARY KEY,
    recommendation_request_id UUID NOT NULL,
    activity_version_id UUID NOT NULL,
    adaptation_rule_id UUID,
    rank INT NOT NULL DEFAULT 1,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_recommendations_request
        FOREIGN KEY (recommendation_request_id) REFERENCES recommendation_requests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recommendations_activity_version
        FOREIGN KEY (activity_version_id) REFERENCES activity_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recommendations_rule FOREIGN KEY (adaptation_rule_id) REFERENCES adaptation_rules (id) ON DELETE RESTRICT,
    CONSTRAINT ck_recommendations_rank CHECK (rank > 0)
);

CREATE INDEX ix_recommendations_request_id ON recommendations (recommendation_request_id);
CREATE INDEX ix_recommendations_activity_version_id ON recommendations (activity_version_id);

CREATE TRIGGER trg_recommendations_immutable
    BEFORE UPDATE OR DELETE ON recommendations
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE recommendation_outcomes (
    id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL,
    activity_attempt_id UUID,
    outcome VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_recommendation_outcomes_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES recommendations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recommendation_outcomes_attempt
        FOREIGN KEY (activity_attempt_id) REFERENCES activity_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT uq_recommendation_outcomes_recommendation UNIQUE (recommendation_id),
    CONSTRAINT ck_recommendation_outcomes_outcome CHECK (outcome IN ('FOLLOWED', 'IGNORED', 'REPLACED'))
);

CREATE INDEX ix_recommendation_outcomes_recommendation_id ON recommendation_outcomes (recommendation_id);

CREATE TRIGGER trg_recommendation_outcomes_immutable
    BEFORE UPDATE OR DELETE ON recommendation_outcomes
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE adaptation_decisions (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    decision_type VARCHAR(30) NOT NULL,
    adaptation_rule_id UUID,
    context JSONB NOT NULL DEFAULT '{}',
    selected_activity_version_id UUID,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_adaptation_decisions_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_adaptation_decisions_rule
        FOREIGN KEY (adaptation_rule_id) REFERENCES adaptation_rules (id) ON DELETE RESTRICT,
    CONSTRAINT fk_adaptation_decisions_activity_version
        FOREIGN KEY (selected_activity_version_id) REFERENCES activity_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_adaptation_decisions_type
        CHECK (decision_type IN ('CONTINUE', 'INCREASE_DIFFICULTY', 'REMEDIATE', 'SCHEDULE_REVIEW'))
);

CREATE INDEX ix_adaptation_decisions_child_id ON adaptation_decisions (child_id);

CREATE TRIGGER trg_adaptation_decisions_immutable
    BEFORE UPDATE OR DELETE ON adaptation_decisions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();
