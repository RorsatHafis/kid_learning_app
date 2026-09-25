CREATE TABLE foundation_elements (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    element_type VARCHAR(30) NOT NULL,
    code VARCHAR(60) NOT NULL,
    display_text VARCHAR(50) NOT NULL,
    romanization VARCHAR(100),
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_foundation_elements_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_foundation_elements_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_foundation_elements_skill UNIQUE (skill_id),
    CONSTRAINT uq_foundation_elements_subject_code UNIQUE (subject_id, code),
    CONSTRAINT ck_foundation_elements_type CHECK (element_type IN (
        'CONSONANT', 'DEPENDENT_VOWEL', 'INDEPENDENT_VOWEL', 'SUBSCRIPT',
        'DIACRITIC', 'NUMERAL', 'COMBINATION', 'SYLLABLE', 'WORD')),
    CONSTRAINT ck_foundation_elements_code_not_blank CHECK (char_length(btrim(code)) > 0),
    CONSTRAINT ck_foundation_elements_display_text_not_blank CHECK (char_length(btrim(display_text)) > 0),
    CONSTRAINT ck_foundation_elements_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_foundation_elements_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_foundation_elements_version CHECK (version >= 0)
);

CREATE INDEX ix_foundation_elements_subject_id ON foundation_elements (subject_id);

CREATE TRIGGER trg_foundation_elements_updated_at
    BEFORE UPDATE ON foundation_elements
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
