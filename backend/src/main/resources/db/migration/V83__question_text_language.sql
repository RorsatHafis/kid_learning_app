-- Screen-reader language of content shown as written (LOCALIZATION.md, owner's direction 2026-10-30): a question
-- version's own text is marked with its language only when that language is known, never guessed.
--
-- text_language = 'en': the version's base text was written in the Studio's English fields (they are labelled
-- English since the bilingual inputs of 2026-10-29), so a reader is told it is English. NULL: unknown - versions
-- written earlier may hold Khmer typed into those fields - and nothing is marked. No backfill: unknown stays unknown;
-- a version gets 'en' when a draft of it is saved in the Studio. Published versions stay as they are.

ALTER TABLE question_versions ADD COLUMN text_language VARCHAR(2);

ALTER TABLE question_versions
    ADD CONSTRAINT ck_question_versions_text_language CHECK (text_language IS NULL OR text_language = 'en');
