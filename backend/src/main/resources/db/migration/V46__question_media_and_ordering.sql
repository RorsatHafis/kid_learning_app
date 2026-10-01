-- X11: authored pictures and recorded audio on questions, and ORDERING questions.
--
-- content_assets (V12) has existed without a writer. It becomes the record of an uploaded file:
-- content-addressed (sha256 of the bytes, which also names the stored file), owned like the content
-- that uses it (N7: null = platform media, uploaded by an administrator; otherwise the author's
-- account), and recorded with who uploaded it. Rows written before X11 (none are known) keep
-- null sha256 and owner; nothing attaches them, because attaching requires a sha256 (MediaService).
ALTER TABLE content_assets
    ADD COLUMN sha256 VARCHAR(64),
    ADD COLUMN owner_account_id UUID,
    ADD COLUMN uploaded_by_account_id UUID,
    ADD CONSTRAINT fk_content_assets_owner FOREIGN KEY (owner_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_content_assets_uploaded_by FOREIGN KEY (uploaded_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_content_assets_sha256 CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$');

-- The same bytes uploaded again by the same owner are the same asset (retries, re-uploads).
CREATE UNIQUE INDEX uq_content_assets_owner_sha256 ON content_assets (owner_account_id, sha256) NULLS NOT DISTINCT
    WHERE sha256 IS NOT NULL;

-- A question version's prompt can carry a picture and a recorded reading; an answer item a picture
-- and a recorded word. Part of the version like its text: set on a draft, copied to the next draft,
-- never changed once published (QuestionVersion, QuestionService). An asset a published version
-- uses can't be deleted (RESTRICT).
ALTER TABLE question_versions
    ADD COLUMN prompt_image_asset_id UUID,
    -- What the picture shows, for children who use a screen reader (it may carry the question:
    -- "how many apples?"). Required to publish a version with a picture (QuestionCompleteness).
    ADD COLUMN prompt_image_description TEXT,
    ADD COLUMN prompt_audio_asset_id UUID,
    ADD CONSTRAINT fk_question_versions_prompt_image FOREIGN KEY (prompt_image_asset_id) REFERENCES content_assets (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_question_versions_prompt_audio FOREIGN KEY (prompt_audio_asset_id) REFERENCES content_assets (id) ON DELETE RESTRICT;

ALTER TABLE question_options
    ADD COLUMN image_asset_id UUID,
    ADD COLUMN audio_asset_id UUID,
    ADD CONSTRAINT fk_question_options_image FOREIGN KEY (image_asset_id) REFERENCES content_assets (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_question_options_audio FOREIGN KEY (audio_asset_id) REFERENCES content_assets (id) ON DELETE RESTRICT;

-- Which content uses an asset (before any deletion or replacement of media is built).
CREATE INDEX ix_question_versions_prompt_image ON question_versions (prompt_image_asset_id) WHERE prompt_image_asset_id IS NOT NULL;
CREATE INDEX ix_question_versions_prompt_audio ON question_versions (prompt_audio_asset_id) WHERE prompt_audio_asset_id IS NOT NULL;
CREATE INDEX ix_question_options_image ON question_options (image_asset_id) WHERE image_asset_id IS NOT NULL;
CREATE INDEX ix_question_options_audio ON question_options (audio_asset_id) WHERE audio_asset_id IS NOT NULL;

-- ORDERING: the items are the version's options, display_order their correct order.
ALTER TABLE questions DROP CONSTRAINT ck_questions_type;
ALTER TABLE questions ADD CONSTRAINT ck_questions_type
    CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'NUMERIC', 'MATCHING', 'ORDERING'));
