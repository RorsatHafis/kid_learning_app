ALTER TABLE curriculums ADD COLUMN IF NOT EXISTS owner_account_id UUID;
ALTER TABLE curriculums ADD CONSTRAINT fk_curriculums_owner_account
    FOREIGN KEY (owner_account_id) REFERENCES accounts(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS ix_curriculums_owner_account_id ON curriculums(owner_account_id);

CREATE TABLE learner_streaks (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL UNIQUE,
    current_streak INTEGER NOT NULL DEFAULT 0,
    longest_streak INTEGER NOT NULL DEFAULT 0,
    last_activity_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_learner_streaks_child FOREIGN KEY(child_id) REFERENCES children(id) ON DELETE RESTRICT,
    CONSTRAINT ck_learner_streaks_current CHECK(current_streak >= 0),
    CONSTRAINT ck_learner_streaks_longest CHECK(longest_streak >= 0)
);
CREATE INDEX ix_learner_streaks_child_id ON learner_streaks(child_id);
CREATE TRIGGER trg_learner_streaks_updated_at BEFORE UPDATE ON learner_streaks FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE challenges (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL,
    activity_version_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    target_completions INTEGER NOT NULL DEFAULT 1,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_challenges_owner FOREIGN KEY(owner_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_challenges_activity_version FOREIGN KEY(activity_version_id) REFERENCES activity_versions(id) ON DELETE RESTRICT,
    CONSTRAINT ck_challenges_title CHECK(char_length(btrim(title)) > 0),
    CONSTRAINT ck_challenges_target CHECK(target_completions > 0),
    CONSTRAINT ck_challenges_status CHECK(status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_challenges_window CHECK(ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX ix_challenges_owner ON challenges(owner_account_id);
CREATE INDEX ix_challenges_activity_version ON challenges(activity_version_id);
CREATE TABLE challenge_classes (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL,
    school_class_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_challenge_classes_challenge FOREIGN KEY(challenge_id) REFERENCES challenges(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_classes_class FOREIGN KEY(school_class_id) REFERENCES school_classes(id) ON DELETE RESTRICT,
    CONSTRAINT uq_challenge_classes UNIQUE(challenge_id, school_class_id)
);
CREATE INDEX ix_challenge_classes_class ON challenge_classes(school_class_id);
CREATE TRIGGER trg_challenges_updated_at BEFORE UPDATE ON challenges FOR EACH ROW EXECUTE FUNCTION set_updated_at();
