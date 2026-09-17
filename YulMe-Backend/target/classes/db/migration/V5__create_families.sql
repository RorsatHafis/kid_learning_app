CREATE TABLE families (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_families_name_not_blank
        CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_families_version
        CHECK (version >= 0)
);

CREATE TRIGGER trg_families_updated_at
    BEFORE UPDATE ON families
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
