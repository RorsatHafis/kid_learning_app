-- M3 (product improvement program): a parent may set how long a child learns each day. The limit lives in the
-- per-child settings column V9 already created and nothing has written yet; this narrows it to the lengths a parent
-- can choose. A new activity doesn't start once today's time is used; one already open always finishes.

ALTER TABLE child_settings
    ADD CONSTRAINT ck_child_settings_daily_time_limit_choices
        CHECK (daily_time_limit_minutes IS NULL OR daily_time_limit_minutes IN (15, 20, 30, 45, 60, 90, 120));

-- A parent's "a little more today": extra minutes for one learning day, recorded (who, when) and summed.
CREATE TABLE child_daily_extensions (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    learning_day DATE NOT NULL,
    minutes INT NOT NULL,
    granted_by_account_id UUID,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_child_daily_extensions_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT ck_child_daily_extensions_minutes CHECK (minutes IN (10, 15, 30))
);

CREATE INDEX ix_child_daily_extensions_child_day ON child_daily_extensions (child_id, learning_day);
