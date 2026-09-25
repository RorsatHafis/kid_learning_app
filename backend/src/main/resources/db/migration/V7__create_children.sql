CREATE TABLE children (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    date_of_birth DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_children_family
        FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE RESTRICT,
    CONSTRAINT ck_children_display_name_not_blank
        CHECK (char_length(btrim(display_name)) > 0),
    CONSTRAINT ck_children_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_children_date_of_birth_plausible
        CHECK (date_of_birth > DATE '1900-01-01'),
    CONSTRAINT ck_children_version
        CHECK (version >= 0)
);

CREATE INDEX ix_children_family_id
    ON children (family_id);

CREATE TRIGGER trg_children_updated_at
    BEFORE UPDATE ON children
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
