-- SORTING questions (X23): the child sorts items into groups ("even or odd", "starts with b or d",
-- "living or not living"). A sorting version's options are its groups and its items: each has a
-- role and a group number; an item belongs to the group with its number. A separate pair of
-- columns, not the MATCHING ones (V40): matching keeps its one-left-one-right-per-pair index, and
-- no option can be both. Options of every other question type leave both columns NULL.
ALTER TABLE question_options
    ADD COLUMN sort_role VARCHAR(5),
    ADD COLUMN sort_group INT;

ALTER TABLE question_options ADD CONSTRAINT ck_question_options_sort
    CHECK ((sort_role IS NULL AND sort_group IS NULL)
        OR (sort_role IN ('GROUP', 'ITEM') AND sort_group >= 0));

ALTER TABLE question_options ADD CONSTRAINT ck_question_options_sort_not_match
    CHECK (sort_role IS NULL OR match_side IS NULL);

-- One group per number in a version (any number of items may share it).
CREATE UNIQUE INDEX uq_question_options_sort_group
    ON question_options (question_version_id, sort_group)
    WHERE sort_role = 'GROUP';

ALTER TABLE questions DROP CONSTRAINT ck_questions_type;
ALTER TABLE questions ADD CONSTRAINT ck_questions_type
    CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'NUMERIC', 'MATCHING', 'ORDERING', 'SORTING'));
