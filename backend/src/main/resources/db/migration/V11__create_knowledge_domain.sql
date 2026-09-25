
CREATE TABLE skills (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_skills_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT uq_skills_code UNIQUE (code),
    CONSTRAINT ck_skills_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_skills_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_skills_version CHECK (version >= 0)
);

CREATE INDEX ix_skills_subject_id ON skills (subject_id);

CREATE TRIGGER trg_skills_updated_at
    BEFORE UPDATE ON skills
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE skill_groups (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_skill_groups_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT uq_skill_groups_code UNIQUE (code),
    CONSTRAINT ck_skill_groups_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_skill_groups_version CHECK (version >= 0)
);

CREATE INDEX ix_skill_groups_subject_id ON skill_groups (subject_id);

CREATE TRIGGER trg_skill_groups_updated_at
    BEFORE UPDATE ON skill_groups
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE skill_group_members (
    id UUID PRIMARY KEY,
    skill_group_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_skill_group_members_group FOREIGN KEY (skill_group_id) REFERENCES skill_groups (id) ON DELETE RESTRICT,
    CONSTRAINT fk_skill_group_members_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_skill_group_members_group_skill UNIQUE (skill_group_id, skill_id),
    CONSTRAINT ck_skill_group_members_display_order CHECK (display_order >= 0)
);

CREATE INDEX ix_skill_group_members_skill_group_id ON skill_group_members (skill_group_id);
CREATE INDEX ix_skill_group_members_skill_id ON skill_group_members (skill_id);

CREATE TABLE skill_prerequisites (
    id UUID PRIMARY KEY,
    skill_id UUID NOT NULL,
    prerequisite_skill_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_skill_prerequisites_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT fk_skill_prerequisites_prerequisite
        FOREIGN KEY (prerequisite_skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_skill_prerequisites_pair UNIQUE (skill_id, prerequisite_skill_id),
    CONSTRAINT ck_skill_prerequisites_not_self CHECK (skill_id <> prerequisite_skill_id)
);

CREATE INDEX ix_skill_prerequisites_skill_id ON skill_prerequisites (skill_id);
CREATE INDEX ix_skill_prerequisites_prerequisite_skill_id ON skill_prerequisites (prerequisite_skill_id);

CREATE TABLE learning_objective_skills (
    id UUID PRIMARY KEY,
    learning_objective_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learning_objective_skills_objective
        FOREIGN KEY (learning_objective_id) REFERENCES learning_objectives (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learning_objective_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learning_objective_skills_pair UNIQUE (learning_objective_id, skill_id)
);

CREATE INDEX ix_learning_objective_skills_objective_id ON learning_objective_skills (learning_objective_id);
CREATE INDEX ix_learning_objective_skills_skill_id ON learning_objective_skills (skill_id);
