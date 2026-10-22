-- M2 (product improvement program): what the public landing page says, managed by administrators, and the few
-- published activities anyone may try before registering.

-- Statements shown on the landing page. BRAND statements are Bookeyond's own words. A TESTIMONIAL is a real person's
-- words, so it needs who said it and a note of where their permission is recorded (administrators only see the note).
CREATE TABLE site_statements (
    id UUID PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    text_en VARCHAR(280) NOT NULL,
    text_km VARCHAR(280) NOT NULL,
    attribution_en VARCHAR(120),
    attribution_km VARCHAR(120),
    source_note VARCHAR(500),
    position INT NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_site_statements_kind CHECK (kind IN ('BRAND', 'TESTIMONIAL')),
    CONSTRAINT ck_site_statements_text CHECK (char_length(btrim(text_en)) > 0 AND char_length(btrim(text_km)) > 0),
    CONSTRAINT ck_site_statements_testimonial CHECK (kind <> 'TESTIMONIAL'
        OR (attribution_en IS NOT NULL AND char_length(btrim(attribution_en)) > 0
            AND attribution_km IS NOT NULL AND char_length(btrim(attribution_km)) > 0
            AND source_note IS NOT NULL AND char_length(btrim(source_note)) > 0)),
    CONSTRAINT ck_site_statements_position CHECK (position >= 0),
    CONSTRAINT ck_site_statements_version CHECK (version >= 0)
);

CREATE INDEX ix_site_statements_visible ON site_statements (position, created_at) WHERE visible;

CREATE TRIGGER trg_site_statements_updated_at
    BEFORE UPDATE ON site_statements
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Three of the brand's own lines, already on the site, as the first statements (no invented quotes or people).
INSERT INTO site_statements (id, kind, text_en, text_km, position, visible) VALUES
    ('5e7a1a00-0000-4000-8000-000000000001', 'BRAND', 'Learn Today, Build Tomorrow', 'រៀនថ្ងៃនេះ កសាងថ្ងៃស្អែក', 1, TRUE),
    ('5e7a1a00-0000-4000-8000-000000000002', 'BRAND', 'A finished lesson doesn''t tell the whole story',
     'មេរៀនដែលបានបញ្ចប់ មិនបានប្រាប់រឿងទាំងស្រុងទេ', 2, TRUE),
    ('5e7a1a00-0000-4000-8000-000000000003', 'BRAND', 'Evidence has a strength. We show it',
     'ភស្តុតាងមានកម្រិតភាពរឹងមាំ។ យើងបង្ហាញវា', 3, TRUE);

-- The activities a visitor may try without an account: exact published versions (pinned, never "the latest"), at most
-- three, platform content only (the service checks both). Nothing a visitor does is stored.
CREATE TABLE public_demo_activities (
    activity_version_id UUID PRIMARY KEY,
    position INT NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_public_demo_activity_version FOREIGN KEY (activity_version_id) REFERENCES activity_versions (id) ON DELETE CASCADE,
    CONSTRAINT ck_public_demo_position CHECK (position BETWEEN 1 AND 3)
);
