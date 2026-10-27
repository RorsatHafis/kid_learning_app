-- D-16 (owner decision 2026-10-27): whether the child opened the question's hint before answering.
-- A right answer given with the hint is practice ("worked through with help"), not evidence that the child
-- showed it on their own, so it does not move mastery or count as a remembered review.
--
-- Nullable on purpose: answers recorded before this column existed were never asked, so they stay NULL
-- ("not recorded") rather than being rewritten as "no hint". Adding a column without a default does not
-- touch existing rows, so the immutability trigger on answer_records (V14) is not involved.
ALTER TABLE answer_records
    ADD COLUMN hint_used BOOLEAN;
