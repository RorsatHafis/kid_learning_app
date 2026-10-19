-- Y41: an educator may write a hint (shown before the child answers, never the answer itself) and an
-- explanation (shown after the answer, with the right answer) for a question. Both are part of the
-- version like its prompt: edited while it is a draft, carried to the next draft, frozen once published.
-- Optional, so every existing question keeps working without them. Written in the curriculum's own
-- language by its author; the interface never translates them.
ALTER TABLE question_versions
    ADD COLUMN hint TEXT,
    ADD COLUMN explanation TEXT,
    ADD CONSTRAINT ck_question_versions_hint_length CHECK (hint IS NULL OR char_length(hint) BETWEEN 1 AND 300),
    ADD CONSTRAINT ck_question_versions_explanation_length
        CHECK (explanation IS NULL OR char_length(explanation) BETWEEN 1 AND 600);
