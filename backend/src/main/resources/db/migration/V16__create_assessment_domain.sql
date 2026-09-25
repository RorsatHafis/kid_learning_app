
CREATE TABLE assessments (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    assessment_type VARCHAR(30) NOT NULL DEFAULT 'PLACEMENT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_assessments_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT ck_assessments_title_not_blank CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT ck_assessments_type CHECK (assessment_type IN ('PLACEMENT', 'PERIODIC', 'DIAGNOSTIC')),
    CONSTRAINT ck_assessments_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_assessments_version CHECK (version >= 0)
);

CREATE INDEX ix_assessments_subject_id ON assessments (subject_id);

CREATE TRIGGER trg_assessments_updated_at
    BEFORE UPDATE ON assessments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE assessment_versions (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    instructions TEXT,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_assessment_versions_assessment
        FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE RESTRICT,
    CONSTRAINT uq_assessment_versions_assessment_number UNIQUE (assessment_id, version_number),
    CONSTRAINT ck_assessment_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_assessment_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_assessment_versions_published_at CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR
        (status IN ('PUBLISHED', 'RETIRED') AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_assessment_versions_version CHECK (version >= 0)
);

CREATE INDEX ix_assessment_versions_assessment_id ON assessment_versions (assessment_id);

CREATE TRIGGER trg_assessment_versions_updated_at
    BEFORE UPDATE ON assessment_versions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE assessment_items (
    id UUID PRIMARY KEY,
    assessment_version_id UUID NOT NULL,
    question_version_id UUID NOT NULL,
    sequence_order INT NOT NULL,
    points NUMERIC(5, 2) NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_assessment_items_assessment_version
        FOREIGN KEY (assessment_version_id) REFERENCES assessment_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assessment_items_question_version
        FOREIGN KEY (question_version_id) REFERENCES question_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_assessment_items_version_order UNIQUE (assessment_version_id, sequence_order),
    CONSTRAINT ck_assessment_items_points CHECK (points > 0)
);

CREATE INDEX ix_assessment_items_assessment_version_id ON assessment_items (assessment_version_id);
CREATE INDEX ix_assessment_items_question_version_id ON assessment_items (question_version_id);

CREATE TABLE assessment_attempts (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    assessment_version_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_assessment_attempts_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assessment_attempts_assessment_version
        FOREIGN KEY (assessment_version_id) REFERENCES assessment_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_assessment_attempts_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_assessment_attempts_completed_at CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL) OR (status <> 'IN_PROGRESS' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_assessment_attempts_version CHECK (version >= 0)
);

CREATE INDEX ix_assessment_attempts_child_id ON assessment_attempts (child_id);
CREATE INDEX ix_assessment_attempts_assessment_version_id ON assessment_attempts (assessment_version_id);

CREATE TRIGGER trg_assessment_attempts_updated_at
    BEFORE UPDATE ON assessment_attempts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE assessment_answers (
    id UUID PRIMARY KEY,
    assessment_attempt_id UUID NOT NULL,
    assessment_item_id UUID NOT NULL,
    submitted_answer TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    points_awarded NUMERIC(5, 2) NOT NULL DEFAULT 0,
    time_spent_seconds INT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_assessment_answers_attempt
        FOREIGN KEY (assessment_attempt_id) REFERENCES assessment_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assessment_answers_item
        FOREIGN KEY (assessment_item_id) REFERENCES assessment_items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_assessment_answers_points_awarded CHECK (points_awarded >= 0),
    CONSTRAINT ck_assessment_answers_time_spent CHECK (time_spent_seconds IS NULL OR time_spent_seconds >= 0)
);

CREATE INDEX ix_assessment_answers_attempt_id ON assessment_answers (assessment_attempt_id);
CREATE INDEX ix_assessment_answers_item_id ON assessment_answers (assessment_item_id);

CREATE TRIGGER trg_assessment_answers_immutable
    BEFORE UPDATE OR DELETE ON assessment_answers
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE assessment_results (
    id UUID PRIMARY KEY,
    assessment_attempt_id UUID NOT NULL,
    total_points NUMERIC(6, 2) NOT NULL,
    points_earned NUMERIC(6, 2) NOT NULL,
    percentage_score NUMERIC(5, 2) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_assessment_results_attempt
        FOREIGN KEY (assessment_attempt_id) REFERENCES assessment_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT uq_assessment_results_attempt UNIQUE (assessment_attempt_id),
    CONSTRAINT ck_assessment_results_total_points CHECK (total_points > 0),
    CONSTRAINT ck_assessment_results_points_earned CHECK (points_earned >= 0 AND points_earned <= total_points),
    CONSTRAINT ck_assessment_results_percentage CHECK (percentage_score >= 0 AND percentage_score <= 100)
);

CREATE TRIGGER trg_assessment_results_immutable
    BEFORE UPDATE OR DELETE ON assessment_results
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE assessment_skill_results (
    id UUID PRIMARY KEY,
    assessment_result_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    points_possible NUMERIC(6, 2) NOT NULL,
    points_earned NUMERIC(6, 2) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_assessment_skill_results_result
        FOREIGN KEY (assessment_result_id) REFERENCES assessment_results (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assessment_skill_results_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_assessment_skill_results_result_skill UNIQUE (assessment_result_id, skill_id),
    CONSTRAINT ck_assessment_skill_results_possible CHECK (points_possible > 0),
    CONSTRAINT ck_assessment_skill_results_earned CHECK (points_earned >= 0 AND points_earned <= points_possible)
);

CREATE INDEX ix_assessment_skill_results_result_id ON assessment_skill_results (assessment_result_id);
CREATE INDEX ix_assessment_skill_results_skill_id ON assessment_skill_results (skill_id);

CREATE TRIGGER trg_assessment_skill_results_immutable
    BEFORE UPDATE OR DELETE ON assessment_skill_results
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();
