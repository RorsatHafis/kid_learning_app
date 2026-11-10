-- Owner-approved 2026-11-10: the Drawing / Handwriting question type.
--
-- A drawing is the child's answer like any other (answer_records: the same storage, export and erasure), but it is
-- not scored: no automatic grading, no mastery, no Smart Review. scored = FALSE marks it; every reader of correctness
-- counts scored answers only (existing answers are all scored). Finishing an activity needs every question answered,
-- drawings included, as before; a blank drawing is refused before anything is recorded.
--
-- drawing_guide: the author's guide on the question version - mode (FREE, TRACE, GUIDED), the reference to copy
-- (English and Khmer text), an optional tracing template drawn by the author, start points, arrows, baseline and grid.
-- Frozen with the version once published, like the rest of it.
--
-- drawing_feedback: a parent's or an assigned teacher's words on a drawing. It goes with the answer it is about.

ALTER TABLE questions DROP CONSTRAINT ck_questions_type;
ALTER TABLE questions ADD CONSTRAINT ck_questions_type
    CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'NUMERIC', 'MATCHING', 'ORDERING', 'SORTING', 'DRAWING'));

ALTER TABLE question_versions ADD COLUMN drawing_guide TEXT;
ALTER TABLE question_versions ADD CONSTRAINT ck_question_versions_drawing_guide_size
    CHECK (drawing_guide IS NULL OR char_length(drawing_guide) <= 60000);

ALTER TABLE answer_records ADD COLUMN scored BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE drawing_feedback (
    id UUID PRIMARY KEY,
    answer_record_id UUID NOT NULL,
    -- Null once that account is erased (the words stay with the answer, like the answer itself).
    author_account_id UUID,
    -- FAMILY (a parent) or SCHOOL (a class's teacher or principal): who may read it (a school sees its own only).
    author_side VARCHAR(10) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_drawing_feedback_answer FOREIGN KEY (answer_record_id) REFERENCES answer_records (id) ON DELETE CASCADE,
    CONSTRAINT fk_drawing_feedback_author FOREIGN KEY (author_account_id) REFERENCES accounts (id) ON DELETE SET NULL,
    CONSTRAINT ck_drawing_feedback_side CHECK (author_side IN ('FAMILY', 'SCHOOL')),
    CONSTRAINT ck_drawing_feedback_body CHECK (char_length(btrim(body)) BETWEEN 1 AND 500)
);

CREATE INDEX idx_drawing_feedback_answer ON drawing_feedback (answer_record_id, created_at);
