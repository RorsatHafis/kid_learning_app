
CREATE TABLE parent_goals (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    goal_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    target_value NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_parent_goals_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_parent_goals_title_not_blank CHECK (char_length(btrim(title)) > 0),
    CONSTRAINT ck_parent_goals_type CHECK (goal_type IN ('DAILY_TIME', 'WEEKLY_ACTIVITIES', 'SKILL_MASTERY')),
    CONSTRAINT ck_parent_goals_target_value CHECK (target_value > 0),
    CONSTRAINT ck_parent_goals_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_parent_goals_version CHECK (version >= 0)
);

CREATE INDEX ix_parent_goals_account_id ON parent_goals (account_id);

CREATE TRIGGER trg_parent_goals_updated_at
    BEFORE UPDATE ON parent_goals
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE child_goals (
    id UUID PRIMARY KEY,
    parent_goal_id UUID NOT NULL,
    child_id UUID NOT NULL,
    current_value NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    achieved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_child_goals_parent_goal FOREIGN KEY (parent_goal_id) REFERENCES parent_goals (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_goals_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT uq_child_goals_parent_goal_child UNIQUE (parent_goal_id, child_id),
    CONSTRAINT ck_child_goals_current_value CHECK (current_value >= 0),
    CONSTRAINT ck_child_goals_status CHECK (status IN ('ACTIVE', 'ACHIEVED', 'ARCHIVED')),
    CONSTRAINT ck_child_goals_achieved_at CHECK (
        (status = 'ACHIEVED' AND achieved_at IS NOT NULL) OR (status <> 'ACHIEVED' AND achieved_at IS NULL)
    ),
    CONSTRAINT ck_child_goals_version CHECK (version >= 0)
);

CREATE INDEX ix_child_goals_parent_goal_id ON child_goals (parent_goal_id);
CREATE INDEX ix_child_goals_child_id ON child_goals (child_id);

CREATE TRIGGER trg_child_goals_updated_at
    BEFORE UPDATE ON child_goals
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE progress_snapshots (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_time_seconds INT NOT NULL DEFAULT 0,
    activities_completed INT NOT NULL DEFAULT 0,
    skills_improved INT NOT NULL DEFAULT 0,
    metrics JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_progress_snapshots_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT uq_progress_snapshots_child_period UNIQUE (child_id, period_start, period_end),
    CONSTRAINT ck_progress_snapshots_period CHECK (period_end >= period_start),
    CONSTRAINT ck_progress_snapshots_total_time CHECK (total_time_seconds >= 0),
    CONSTRAINT ck_progress_snapshots_activities CHECK (activities_completed >= 0),
    CONSTRAINT ck_progress_snapshots_skills_improved CHECK (skills_improved >= 0)
);

CREATE INDEX ix_progress_snapshots_child_id ON progress_snapshots (child_id);

CREATE TRIGGER trg_progress_snapshots_immutable
    BEFORE UPDATE OR DELETE ON progress_snapshots
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE parent_insights (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    insight_type VARCHAR(30) NOT NULL,
    headline TEXT NOT NULL,
    detail TEXT,
    supporting_data JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_parent_insights_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT ck_parent_insights_type CHECK (insight_type IN ('PROGRESS', 'WEAKNESS', 'STRENGTH', 'RECOMMENDATION')),
    CONSTRAINT ck_parent_insights_headline_not_blank CHECK (char_length(btrim(headline)) > 0)
);

CREATE INDEX ix_parent_insights_child_id ON parent_insights (child_id);

CREATE TRIGGER trg_parent_insights_immutable
    BEFORE UPDATE OR DELETE ON parent_insights
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE parent_alerts (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    child_id UUID NOT NULL,
    alert_type VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    read_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_parent_alerts_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_parent_alerts_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT ck_parent_alerts_type
        CHECK (alert_type IN ('INACTIVITY', 'STRUGGLING', 'GOAL_AT_RISK', 'GOAL_ACHIEVED')),
    CONSTRAINT ck_parent_alerts_message_not_blank CHECK (char_length(btrim(message)) > 0),
    CONSTRAINT ck_parent_alerts_status CHECK (status IN ('UNREAD', 'READ', 'DISMISSED')),
    CONSTRAINT ck_parent_alerts_version CHECK (version >= 0)
);

CREATE INDEX ix_parent_alerts_account_id ON parent_alerts (account_id);
CREATE INDEX ix_parent_alerts_child_id ON parent_alerts (child_id);

CREATE TRIGGER trg_parent_alerts_updated_at
    BEFORE UPDATE ON parent_alerts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
