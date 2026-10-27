-- F1a (owner, 2026-10-27): every word on the public pages editable without changing code. The pages' words keep living
-- in the frontend's message catalogs as defaults; an administrator's override for one entry (English, Khmer or both) is
-- stored here and laid over the default. Deleting the row puts the default back.
--
-- namespace: the catalog (only the public pages' catalogs are accepted - checked in SiteCopyService and here);
-- copy_key: the entry's path in it, e.g. "hero.title". Text is plain (no markup); {1}, {2}... stand for the values an
-- entry is given (e.g. the year in the footer).
CREATE TABLE site_copy (
    namespace VARCHAR(60) NOT NULL,
    copy_key VARCHAR(120) NOT NULL,
    text_en VARCHAR(600),
    text_km VARCHAR(600),
    updated_by UUID REFERENCES accounts (id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (namespace, copy_key),
    CONSTRAINT ck_site_copy_namespace CHECK (namespace IN ('features/marketing', 'features/marketing/trust', 'features/marketing/try')),
    CONSTRAINT ck_site_copy_key CHECK (copy_key ~ '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*$'),
    CONSTRAINT ck_site_copy_some_text CHECK ((text_en IS NOT NULL AND char_length(btrim(text_en)) > 0)
        OR (text_km IS NOT NULL AND char_length(btrim(text_km)) > 0)),
    CONSTRAINT ck_site_copy_version CHECK (version >= 0)
);

CREATE TRIGGER trg_site_copy_updated_at
    BEFORE UPDATE ON site_copy
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
