-- X13: uploaded media becomes a library authors reuse instead of uploading the same file again.
--
-- title: how authors find it ("Three red apples"); description: what it shows, offered as the text a
-- screen reader reads wherever it is used; width/height: a picture's size as inspected at upload;
-- archived_at: taken out of the library and pickers (what already uses it keeps working).
-- Existing rows keep nulls: untitled, undescribed, not archived.
ALTER TABLE content_assets
    ADD COLUMN title VARCHAR(200),
    ADD COLUMN description TEXT,
    ADD COLUMN width INT,
    ADD COLUMN height INT,
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_content_assets_title_not_blank CHECK (title IS NULL OR char_length(btrim(title)) > 0),
    ADD CONSTRAINT ck_content_assets_dimensions CHECK ((width IS NULL AND height IS NULL) OR (width > 0 AND height > 0));

-- The library lists an owner's media newest first (platform media: owner null).
CREATE INDEX ix_content_assets_library ON content_assets (owner_account_id, created_at DESC) WHERE sha256 IS NOT NULL;
