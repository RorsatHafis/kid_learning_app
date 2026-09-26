-- MATCHING questions (L9): a question version's options become the two columns of a
-- matching exercise. Each option is on the LEFT or RIGHT side and belongs to a numbered
-- pair; the left and right option of one pair are each other's match. Options of every
-- other question type leave both columns NULL, exactly as before.
ALTER TABLE question_options
    ADD COLUMN match_side VARCHAR(5),
    ADD COLUMN match_pair INT;

ALTER TABLE question_options ADD CONSTRAINT ck_question_options_match
    CHECK ((match_side IS NULL AND match_pair IS NULL)
        OR (match_side IN ('LEFT', 'RIGHT') AND match_pair >= 0));

-- One left and one right per pair.
CREATE UNIQUE INDEX uq_question_options_match
    ON question_options (question_version_id, match_side, match_pair)
    WHERE match_side IS NOT NULL;
