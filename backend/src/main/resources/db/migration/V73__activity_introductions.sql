-- Learning-experience priority (2026-10-25): an activity can teach before it asks. An optional introduction on each
-- activity version - a short explanation or worked example, a picture (with the description a screen reader reads)
-- and a recording for children who don't read yet - shown to the child before practising and reachable again while
-- practising. Versioned with the activity: a published version's introduction never changes; a new draft copies it.
ALTER TABLE activity_versions
    ADD COLUMN introduction TEXT,
    ADD COLUMN introduction_image_asset_id UUID,
    ADD COLUMN introduction_image_description VARCHAR(300),
    ADD COLUMN introduction_audio_asset_id UUID,
    ADD CONSTRAINT ck_activity_versions_introduction CHECK (introduction IS NULL OR char_length(btrim(introduction)) BETWEEN 1 AND 1200),
    ADD CONSTRAINT ck_activity_versions_introduction_image_description
        CHECK (introduction_image_description IS NULL OR char_length(btrim(introduction_image_description)) > 0),
    ADD CONSTRAINT fk_activity_versions_introduction_image FOREIGN KEY (introduction_image_asset_id) REFERENCES content_assets (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_activity_versions_introduction_audio FOREIGN KEY (introduction_audio_asset_id) REFERENCES content_assets (id) ON DELETE RESTRICT;

CREATE INDEX ix_activity_versions_introduction_image ON activity_versions (introduction_image_asset_id) WHERE introduction_image_asset_id IS NOT NULL;
CREATE INDEX ix_activity_versions_introduction_audio ON activity_versions (introduction_audio_asset_id) WHERE introduction_audio_asset_id IS NOT NULL;
