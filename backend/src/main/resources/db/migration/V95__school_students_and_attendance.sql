-- M12, first module (owner decisions 2026-11-11): attendance, on the school's own student records.
-- A school student record is the school's and separate from any family's child profile: no link to `children` here
-- (a link needs the parent/guardian's explicit consent, built separately), and nothing of a family's learning history
-- is merged in. Minimal fields until the qualified legal review (S6): a name and the school's own number.
CREATE TABLE school_students (
    id             UUID PRIMARY KEY,
    school_id      UUID         NOT NULL REFERENCES schools (id) ON DELETE RESTRICT,
    full_name      VARCHAR(120) NOT NULL,
    student_number VARCHAR(40),
    created_by     UUID         REFERENCES accounts (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_school_students_name CHECK (char_length(btrim(full_name)) > 0),
    CONSTRAINT ck_school_students_number CHECK (student_number IS NULL OR char_length(btrim(student_number)) > 0),
    CONSTRAINT uq_school_students_number UNIQUE (school_id, student_number)
);
CREATE INDEX ix_school_students_school ON school_students (school_id);

-- Which class a student is in, as history: leaving ends the row (never deleted), joining another class starts a new one.
CREATE TABLE class_students (
    id              UUID PRIMARY KEY,
    school_class_id UUID        NOT NULL REFERENCES school_classes (id) ON DELETE RESTRICT,
    student_id      UUID        NOT NULL REFERENCES school_students (id) ON DELETE RESTRICT,
    started_on      DATE        NOT NULL,
    ended_on        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_class_students_dates CHECK (ended_on IS NULL OR ended_on >= started_on)
);
CREATE UNIQUE INDEX uq_class_students_current ON class_students (school_class_id, student_id) WHERE ended_on IS NULL;
CREATE INDEX ix_class_students_student ON class_students (student_id);

-- One mark per student per day (per-day attendance, S2).
CREATE TABLE attendance_marks (
    id              UUID PRIMARY KEY,
    school_id       UUID         NOT NULL REFERENCES schools (id) ON DELETE RESTRICT,
    school_class_id UUID         NOT NULL REFERENCES school_classes (id) ON DELETE RESTRICT,
    student_id      UUID         NOT NULL REFERENCES school_students (id) ON DELETE RESTRICT,
    day             DATE         NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    note            VARCHAR(300),
    recorded_by     UUID         REFERENCES accounts (id) ON DELETE SET NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_attendance_marks_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    CONSTRAINT uq_attendance_marks_student_day UNIQUE (student_id, day)
);
CREATE INDEX ix_attendance_marks_class_day ON attendance_marks (school_class_id, day);

-- Every change to a recorded mark: what it was, what it became, who and why. Append-only.
CREATE TABLE attendance_corrections (
    id           UUID PRIMARY KEY,
    mark_id      UUID         NOT NULL REFERENCES attendance_marks (id) ON DELETE RESTRICT,
    old_status   VARCHAR(16)  NOT NULL,
    new_status   VARCHAR(16)  NOT NULL,
    old_note     VARCHAR(300),
    new_note     VARCHAR(300),
    reason       VARCHAR(300) NOT NULL,
    corrected_by UUID         REFERENCES accounts (id) ON DELETE SET NULL,
    corrected_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_attendance_corrections_reason CHECK (char_length(btrim(reason)) > 0)
);
CREATE INDEX ix_attendance_corrections_mark ON attendance_corrections (mark_id);

CREATE TRIGGER trg_attendance_corrections_immutable
    BEFORE UPDATE OR DELETE ON attendance_corrections
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();
