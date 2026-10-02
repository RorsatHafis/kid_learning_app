-- X15: the administrator's Learning operations and Reports read recent activity by time - completions,
-- answers, path placements, new families - over weeks. Without these, each of those reads scans the whole
-- table, which grows with every answer a child gives. Partial where only one status is ever read.
CREATE INDEX ix_activity_attempts_completed_at ON activity_attempts (completed_at) WHERE status = 'COMPLETED';
CREATE INDEX ix_activity_attempts_started_at ON activity_attempts (started_at);
CREATE INDEX ix_answer_records_occurred_at ON answer_records (occurred_at);
CREATE INDEX ix_learning_path_items_added_at ON learning_path_items (added_at);
CREATE INDEX ix_families_created_at ON families (created_at);
