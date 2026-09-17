ALTER TABLE lessons ADD COLUMN owner_account_id UUID;
ALTER TABLE lessons
    ADD CONSTRAINT fk_lessons_owner_account FOREIGN KEY (owner_account_id) REFERENCES accounts (id) ON DELETE RESTRICT;
CREATE INDEX ix_lessons_owner_account_id ON lessons (owner_account_id);

ALTER TABLE questions ADD COLUMN owner_account_id UUID;
ALTER TABLE questions
    ADD CONSTRAINT fk_questions_owner_account FOREIGN KEY (owner_account_id) REFERENCES accounts (id) ON DELETE RESTRICT;
CREATE INDEX ix_questions_owner_account_id ON questions (owner_account_id);
