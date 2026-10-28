-- M12 P1 (2026-10-28): a school's academic years and terms - its own dates (public schools in Cambodia broadly run
-- November to August, private and international schools differ), so nothing about the calendar is fixed in code.
-- One year per school is current. Classes belong to a year (nullable: classes made before this have none).
-- Overlaps between a school's years, and between a year's terms, are refused by the service under a lock on the
-- school's years (an exclusion constraint would need btree_gist).

CREATE TABLE academic_years (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_academic_years_school FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT ck_academic_years_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_academic_years_dates CHECK (ends_on > starts_on),
    CONSTRAINT ck_academic_years_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_academic_years_current_active CHECK (NOT (is_current AND status = 'ARCHIVED')),
    CONSTRAINT ck_academic_years_version CHECK (version >= 0),
    CONSTRAINT uq_academic_years_school_name UNIQUE (school_id, name)
);

-- At most one current year per school.
CREATE UNIQUE INDEX ux_academic_years_one_current ON academic_years (school_id) WHERE is_current;

CREATE TRIGGER trg_academic_years_updated_at
    BEFORE UPDATE ON academic_years
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE academic_terms (
    id UUID PRIMARY KEY,
    academic_year_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_academic_terms_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT ck_academic_terms_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_academic_terms_dates CHECK (ends_on > starts_on),
    CONSTRAINT ck_academic_terms_version CHECK (version >= 0),
    CONSTRAINT uq_academic_terms_year_name UNIQUE (academic_year_id, name)
);

CREATE INDEX ix_academic_terms_year ON academic_terms (academic_year_id);

CREATE TRIGGER trg_academic_terms_updated_at
    BEFORE UPDATE ON academic_terms
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE school_classes ADD COLUMN academic_year_id UUID;
ALTER TABLE school_classes ADD CONSTRAINT fk_school_classes_academic_year
    FOREIGN KEY (academic_year_id) REFERENCES academic_years (id) ON DELETE RESTRICT;
CREATE INDEX ix_school_classes_academic_year ON school_classes (academic_year_id);
