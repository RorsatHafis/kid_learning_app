-- The admin audit log pages through audit_events newest first, optionally filtered by
-- action (AdminOperationsService). The table only ever grows, so both need an index.
-- id breaks ties between events recorded in the same instant.
CREATE INDEX ix_audit_events_occurred_at ON audit_events (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_events_action_occurred_at ON audit_events (action, occurred_at DESC, id DESC);
