-- Content in two languages, slice 3 (LOCALIZATION.md): the names children and families read on the catalog - a
-- subject's name, a skill's name and description, a learning objective's (a chapter's) title and description, and a
-- curriculum's name - get their Khmer in the existing content_localizations table, beside the English kept on each
-- row. Only the allowed content types widen; no row changes and nothing is copied.
-- These catalog rows aren't versioned, so their translation isn't either: a name's Khmer is the Khmer of that name,
-- wherever it is shown, like the English.

ALTER TABLE content_localizations DROP CONSTRAINT ck_content_localizations_content_type;

ALTER TABLE content_localizations
    ADD CONSTRAINT ck_content_localizations_content_type
        CHECK (content_type IN ('LESSON_VERSION', 'ACTIVITY_VERSION', 'QUESTION_VERSION',
                                'SUBJECT', 'SKILL', 'LEARNING_OBJECTIVE', 'CURRICULUM'));
