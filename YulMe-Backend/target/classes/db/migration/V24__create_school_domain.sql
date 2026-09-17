CREATE TABLE schools (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    principal_account_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_schools_principal_account
        FOREIGN KEY (principal_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_schools_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_schools_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_schools_version CHECK (version >= 0)
);

CREATE INDEX ix_schools_principal_account_id ON schools (principal_account_id);

CREATE TRIGGER trg_schools_updated_at
    BEFORE UPDATE ON schools
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE school_classes (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_school_classes_school FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT ck_school_classes_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_school_classes_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_school_classes_version CHECK (version >= 0)
);

CREATE INDEX ix_school_classes_school_id ON school_classes (school_id);

CREATE TRIGGER trg_school_classes_updated_at
    BEFORE UPDATE ON school_classes
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE teacher_assignments (
    id UUID PRIMARY KEY,
    school_class_id UUID NOT NULL,
    teacher_account_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_teacher_assignments_class
        FOREIGN KEY (school_class_id) REFERENCES school_classes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_assignments_teacher
        FOREIGN KEY (teacher_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT uq_teacher_assignments_class_teacher UNIQUE (school_class_id, teacher_account_id),
    CONSTRAINT ck_teacher_assignments_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_teacher_assignments_version CHECK (version >= 0)
);

CREATE INDEX ix_teacher_assignments_teacher_account_id ON teacher_assignments (teacher_account_id);
CREATE INDEX ix_teacher_assignments_school_class_id ON teacher_assignments (school_class_id);

CREATE TRIGGER trg_teacher_assignments_updated_at
    BEFORE UPDATE ON teacher_assignments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE class_enrollments (
    id UUID PRIMARY KEY,
    school_class_id UUID NOT NULL,
    child_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_class_enrollments_class
        FOREIGN KEY (school_class_id) REFERENCES school_classes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_enrollments_child
        FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT uq_class_enrollments_class_child UNIQUE (school_class_id, child_id),
    CONSTRAINT ck_class_enrollments_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_class_enrollments_version CHECK (version >= 0)
);

CREATE INDEX ix_class_enrollments_child_id ON class_enrollments (child_id);
CREATE INDEX ix_class_enrollments_school_class_id ON class_enrollments (school_class_id);

CREATE TRIGGER trg_class_enrollments_updated_at
    BEFORE UPDATE ON class_enrollments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
