
CREATE TABLE subjects (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_subjects_code UNIQUE (code),
    CONSTRAINT ck_subjects_code_format CHECK (code ~ '^[A-Z0-9_]+$'),
    CONSTRAINT ck_subjects_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_subjects_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_subjects_version CHECK (version >= 0)
);

CREATE TRIGGER trg_subjects_updated_at
    BEFORE UPDATE ON subjects
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE age_hubs (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    min_age INT NOT NULL,
    max_age INT,
    display_order INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_age_hubs_code UNIQUE (code),
    CONSTRAINT ck_age_hubs_code_format CHECK (code ~ '^[A-Z0-9_]+$'),
    CONSTRAINT ck_age_hubs_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_age_hubs_min_age CHECK (min_age >= 0),
    CONSTRAINT ck_age_hubs_age_range CHECK (max_age IS NULL OR max_age >= min_age),
    CONSTRAINT ck_age_hubs_version CHECK (version >= 0)
);

CREATE TRIGGER trg_age_hubs_updated_at
    BEFORE UPDATE ON age_hubs
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE grade_levels (
    id UUID PRIMARY KEY,
    age_hub_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    display_order INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_grade_levels_age_hub FOREIGN KEY (age_hub_id) REFERENCES age_hubs (id) ON DELETE RESTRICT,
    CONSTRAINT uq_grade_levels_code UNIQUE (code),
    CONSTRAINT ck_grade_levels_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_grade_levels_version CHECK (version >= 0)
);

CREATE INDEX ix_grade_levels_age_hub_id ON grade_levels (age_hub_id);

CREATE TRIGGER trg_grade_levels_updated_at
    BEFORE UPDATE ON grade_levels
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE curriculums (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    age_hub_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_curriculums_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_curriculums_age_hub FOREIGN KEY (age_hub_id) REFERENCES age_hubs (id) ON DELETE RESTRICT,
    CONSTRAINT ck_curriculums_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_curriculums_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_curriculums_version CHECK (version >= 0)
);

CREATE INDEX ix_curriculums_subject_id ON curriculums (subject_id);
CREATE INDEX ix_curriculums_age_hub_id ON curriculums (age_hub_id);

CREATE TRIGGER trg_curriculums_updated_at
    BEFORE UPDATE ON curriculums
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE curriculum_versions (
    id UUID PRIMARY KEY,
    curriculum_id UUID NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_curriculum_versions_curriculum
        FOREIGN KEY (curriculum_id) REFERENCES curriculums (id) ON DELETE RESTRICT,
    CONSTRAINT uq_curriculum_versions_curriculum_number UNIQUE (curriculum_id, version_number),
    CONSTRAINT ck_curriculum_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_curriculum_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_curriculum_versions_published_at CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR
        (status IN ('PUBLISHED', 'RETIRED') AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_curriculum_versions_version CHECK (version >= 0)
);

CREATE INDEX ix_curriculum_versions_curriculum_id ON curriculum_versions (curriculum_id);

CREATE TRIGGER trg_curriculum_versions_updated_at
    BEFORE UPDATE ON curriculum_versions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE learning_objectives (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learning_objectives_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learning_objectives_code UNIQUE (code),
    CONSTRAINT ck_learning_objectives_title_not_blank CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT ck_learning_objectives_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_learning_objectives_version CHECK (version >= 0)
);

CREATE INDEX ix_learning_objectives_subject_id ON learning_objectives (subject_id);

CREATE TRIGGER trg_learning_objectives_updated_at
    BEFORE UPDATE ON learning_objectives
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE curriculum_objectives (
    id UUID PRIMARY KEY,
    curriculum_version_id UUID NOT NULL,
    learning_objective_id UUID NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_curriculum_objectives_curriculum_version
        FOREIGN KEY (curriculum_version_id) REFERENCES curriculum_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_curriculum_objectives_learning_objective
        FOREIGN KEY (learning_objective_id) REFERENCES learning_objectives (id) ON DELETE RESTRICT,
    CONSTRAINT uq_curriculum_objectives_version_objective
        UNIQUE (curriculum_version_id, learning_objective_id),
    CONSTRAINT ck_curriculum_objectives_display_order CHECK (display_order >= 0)
);

CREATE INDEX ix_curriculum_objectives_curriculum_version_id ON curriculum_objectives (curriculum_version_id);
CREATE INDEX ix_curriculum_objectives_learning_objective_id ON curriculum_objectives (learning_objective_id);
