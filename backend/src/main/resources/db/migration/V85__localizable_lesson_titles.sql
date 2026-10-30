-- Content in two languages (LOCALIZATION.md, owner's widened brief 2026-10-30): a lesson's title in Khmer, in the
-- existing content_localizations table beside the English on the lesson (field 'name', as for catalog names).
-- Teachers and their colleagues read lesson titles (the Studio, shared lessons). Only the allowed types widen.

ALTER TABLE content_localizations DROP CONSTRAINT ck_content_localizations_content_type;

ALTER TABLE content_localizations
    ADD CONSTRAINT ck_content_localizations_content_type
        CHECK (content_type IN ('LESSON_VERSION', 'ACTIVITY_VERSION', 'QUESTION_VERSION',
                                'SUBJECT', 'SKILL', 'LEARNING_OBJECTIVE', 'CURRICULUM', 'LESSON'));
