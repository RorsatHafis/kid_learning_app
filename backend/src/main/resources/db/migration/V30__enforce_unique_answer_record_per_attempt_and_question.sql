CREATE UNIQUE INDEX ux_answer_records_attempt_question
    ON answer_records (activity_attempt_id, question_version_id);
