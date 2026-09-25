CREATE UNIQUE INDEX ux_activity_attempts_child_version_in_progress
    ON activity_attempts (child_id, activity_version_id)
    WHERE status = 'IN_PROGRESS';

CREATE UNIQUE INDEX ux_activity_items_version_question
    ON activity_items (activity_version_id, question_version_id);
