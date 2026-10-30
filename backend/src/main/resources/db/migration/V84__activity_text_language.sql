-- Screen-reader language of an activity's own words (LOCALIZATION.md; V83 did the same for questions): 'en' when the
-- version's words - and the activity's name shown with it - were written in the Studio's English fields, NULL when
-- unknown (older content may hold Khmer in those fields). No backfill: unknown stays unknown.

ALTER TABLE activity_versions ADD COLUMN text_language VARCHAR(2);

ALTER TABLE activity_versions
    ADD CONSTRAINT ck_activity_versions_text_language CHECK (text_language IS NULL OR text_language = 'en');
