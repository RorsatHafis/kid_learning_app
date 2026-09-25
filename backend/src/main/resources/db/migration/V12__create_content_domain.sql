
CREATE TABLE lessons (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_lessons_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT ck_lessons_title_not_blank CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT ck_lessons_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_lessons_version CHECK (version >= 0)
);

CREATE INDEX ix_lessons_subject_id ON lessons (subject_id);

CREATE TRIGGER trg_lessons_updated_at
    BEFORE UPDATE ON lessons
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE lesson_versions (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    body TEXT,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_lesson_versions_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE RESTRICT,
    CONSTRAINT uq_lesson_versions_lesson_number UNIQUE (lesson_id, version_number),
    CONSTRAINT ck_lesson_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_lesson_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_lesson_versions_published_at CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR
        (status IN ('PUBLISHED', 'RETIRED') AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_lesson_versions_version CHECK (version >= 0)
);

CREATE INDEX ix_lesson_versions_lesson_id ON lesson_versions (lesson_id);

CREATE TRIGGER trg_lesson_versions_updated_at
    BEFORE UPDATE ON lesson_versions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE activities (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    lesson_id UUID,
    activity_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_activities_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activities_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE RESTRICT,
    CONSTRAINT ck_activities_type_not_blank CHECK (char_length(btrim(activity_type)) > 0),
    CONSTRAINT ck_activities_title_not_blank CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT ck_activities_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_activities_version CHECK (version >= 0)
);

CREATE INDEX ix_activities_subject_id ON activities (subject_id);
CREATE INDEX ix_activities_lesson_id ON activities (lesson_id);

CREATE TRIGGER trg_activities_updated_at
    BEFORE UPDATE ON activities
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE activity_versions (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    instructions TEXT,
    difficulty_level INT,
    estimated_duration_seconds INT,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_activity_versions_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE RESTRICT,
    CONSTRAINT uq_activity_versions_activity_number UNIQUE (activity_id, version_number),
    CONSTRAINT ck_activity_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_activity_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_activity_versions_published_at CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR
        (status IN ('PUBLISHED', 'RETIRED') AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_activity_versions_difficulty CHECK (difficulty_level IS NULL OR difficulty_level BETWEEN 1 AND 10),
    CONSTRAINT ck_activity_versions_duration
        CHECK (estimated_duration_seconds IS NULL OR estimated_duration_seconds > 0),
    CONSTRAINT ck_activity_versions_version CHECK (version >= 0)
);

CREATE INDEX ix_activity_versions_activity_id ON activity_versions (activity_id);

CREATE TRIGGER trg_activity_versions_updated_at
    BEFORE UPDATE ON activity_versions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE questions (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_questions_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT ck_questions_type
        CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'NUMERIC', 'MATCHING')),
    CONSTRAINT ck_questions_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_questions_version CHECK (version >= 0)
);

CREATE INDEX ix_questions_subject_id ON questions (subject_id);

CREATE TRIGGER trg_questions_updated_at
    BEFORE UPDATE ON questions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE question_versions (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    prompt TEXT NOT NULL,
    correct_answer TEXT,
    difficulty_level INT,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_question_versions_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_question_versions_question_number UNIQUE (question_id, version_number),
    CONSTRAINT ck_question_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_question_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_question_versions_prompt_not_blank CHECK (char_length(btrim(prompt)) > 0),
    CONSTRAINT ck_question_versions_published_at CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR
        (status IN ('PUBLISHED', 'RETIRED') AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_question_versions_difficulty CHECK (difficulty_level IS NULL OR difficulty_level BETWEEN 1 AND 10),
    CONSTRAINT ck_question_versions_version CHECK (version >= 0)
);

CREATE INDEX ix_question_versions_question_id ON question_versions (question_id);

CREATE TRIGGER trg_question_versions_updated_at
    BEFORE UPDATE ON question_versions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE question_options (
    id UUID PRIMARY KEY,
    question_version_id UUID NOT NULL,
    label TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT false,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_question_options_question_version
        FOREIGN KEY (question_version_id) REFERENCES question_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_question_options_label_not_blank CHECK (char_length(btrim(label)) > 0),
    CONSTRAINT ck_question_options_display_order CHECK (display_order >= 0)
);

CREATE INDEX ix_question_options_question_version_id ON question_options (question_version_id);

CREATE TABLE question_skills (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_question_skills_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_question_skills_pair UNIQUE (question_id, skill_id)
);

CREATE INDEX ix_question_skills_question_id ON question_skills (question_id);
CREATE INDEX ix_question_skills_skill_id ON question_skills (skill_id);

CREATE TABLE activity_skills (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_activity_skills_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_activity_skills_pair UNIQUE (activity_id, skill_id)
);

CREATE INDEX ix_activity_skills_activity_id ON activity_skills (activity_id);
CREATE INDEX ix_activity_skills_skill_id ON activity_skills (skill_id);

CREATE TABLE lesson_skills (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_lesson_skills_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lesson_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_lesson_skills_pair UNIQUE (lesson_id, skill_id)
);

CREATE INDEX ix_lesson_skills_lesson_id ON lesson_skills (lesson_id);
CREATE INDEX ix_lesson_skills_skill_id ON lesson_skills (skill_id);

CREATE TABLE content_assets (
    id UUID PRIMARY KEY,
    asset_type VARCHAR(30) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_content_assets_type CHECK (asset_type IN ('IMAGE', 'AUDIO', 'VIDEO', 'DOCUMENT')),
    CONSTRAINT ck_content_assets_storage_key_not_blank CHECK (char_length(btrim(storage_key)) > 0),
    CONSTRAINT ck_content_assets_mime_type_not_blank CHECK (char_length(btrim(mime_type)) > 0),
    CONSTRAINT ck_content_assets_size_bytes CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE TABLE content_localizations (
    id UUID PRIMARY KEY,
    content_type VARCHAR(30) NOT NULL,
    content_id UUID NOT NULL,
    locale VARCHAR(10) NOT NULL,
    field_name VARCHAR(60) NOT NULL,
    localized_text TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_content_localizations_content_type
        CHECK (content_type IN ('LESSON_VERSION', 'ACTIVITY_VERSION', 'QUESTION_VERSION')),
    CONSTRAINT ck_content_localizations_locale_format CHECK (locale ~ '^[a-z]{2}(-[A-Z]{2})?$'),
    CONSTRAINT ck_content_localizations_field_name_not_blank CHECK (char_length(btrim(field_name)) > 0),
    CONSTRAINT ck_content_localizations_text_not_blank CHECK (char_length(btrim(localized_text)) > 0),
    CONSTRAINT uq_content_localizations_target UNIQUE (content_type, content_id, locale, field_name),
    CONSTRAINT ck_content_localizations_version CHECK (version >= 0)
);

CREATE INDEX ix_content_localizations_content_type_id ON content_localizations (content_type, content_id);

CREATE TRIGGER trg_content_localizations_updated_at
    BEFORE UPDATE ON content_localizations
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
