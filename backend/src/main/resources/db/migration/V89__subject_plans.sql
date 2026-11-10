-- Owner requirement 2026-11-10: a subject is assigned to the family plans that include it (Content Studio).
--
-- A subject with no rows here is in every family plan - every subject that exists today, and a new one until an
-- administrator chooses its plans - so nothing a family has changes with this migration. With rows, only those plans
-- include it: a family on another plan doesn't see its curricula and can't start, switch to or play them (a journey a
-- school class assigns is the school's and is not affected). Plans and subjects are never deleted (V42, X14), so the
-- references are kept; a duplicated plan (a new version, V79) is given its source's subjects.

CREATE TABLE subject_plans (
    subject_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_subject_plans PRIMARY KEY (subject_id, plan_id),
    CONSTRAINT fk_subject_plans_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_subject_plans_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE RESTRICT
);

CREATE INDEX idx_subject_plans_plan ON subject_plans (plan_id);
