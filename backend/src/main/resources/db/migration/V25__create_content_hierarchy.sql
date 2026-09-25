
CREATE TABLE topics (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_topics_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT uq_topics_subject_code UNIQUE (subject_id, code),
    CONSTRAINT ck_topics_code_not_blank CHECK (char_length(btrim(code)) > 0),
    CONSTRAINT ck_topics_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_topics_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_topics_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_topics_version CHECK (version >= 0)
);

CREATE INDEX ix_topics_subject_id ON topics (subject_id);

CREATE TRIGGER trg_topics_updated_at
    BEFORE UPDATE ON topics
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE lessons ADD COLUMN topic_id UUID;
ALTER TABLE lessons
    ADD CONSTRAINT fk_lessons_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE RESTRICT;
CREATE INDEX ix_lessons_topic_id ON lessons (topic_id);

CREATE TABLE learning_units (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learning_units_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE RESTRICT,
    CONSTRAINT ck_learning_units_title_not_blank CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT ck_learning_units_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_learning_units_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_learning_units_version CHECK (version >= 0)
);

CREATE INDEX ix_learning_units_lesson_id ON learning_units (lesson_id);

CREATE TRIGGER trg_learning_units_updated_at
    BEFORE UPDATE ON learning_units
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE activities ADD COLUMN learning_unit_id UUID;
ALTER TABLE activities
    ADD CONSTRAINT fk_activities_learning_unit FOREIGN KEY (learning_unit_id) REFERENCES learning_units (id) ON DELETE RESTRICT;
CREATE INDEX ix_activities_learning_unit_id ON activities (learning_unit_id);
