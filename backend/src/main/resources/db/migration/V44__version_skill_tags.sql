-- TD-24 / PD-08: skill tags become part of a content version, like its items and difficulty.
-- Until now activity_skills and question_skills belonged to the activity or question itself, so
-- retagging after publishing changed what a published version is placed for (and which skills its
-- answers are evidence for) without a new version and without the publish checks. From here a
-- version's tags are edited only while it is a DRAFT and are frozen once it is published; changing
-- them means a new version, which passes the same checks as any other change.

CREATE TABLE activity_version_skills (
    id UUID PRIMARY KEY,
    activity_version_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_activity_version_skills_version FOREIGN KEY (activity_version_id) REFERENCES activity_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_version_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_activity_version_skills_pair UNIQUE (activity_version_id, skill_id)
);

CREATE INDEX ix_activity_version_skills_skill_id ON activity_version_skills (skill_id);

CREATE TABLE question_version_skills (
    id UUID PRIMARY KEY,
    question_version_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_question_version_skills_version FOREIGN KEY (question_version_id) REFERENCES question_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_version_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_question_version_skills_pair UNIQUE (question_version_id, skill_id)
);

CREATE INDEX ix_question_version_skills_skill_id ON question_version_skills (skill_id);

-- Every existing version gets the tags its activity or question has now: the only record there is of
-- them (earlier retags weren't recorded). created_at is kept so placement order stays what it was.
INSERT INTO activity_version_skills (id, activity_version_id, skill_id, created_at)
SELECT gen_random_uuid(), v.id, s.skill_id, s.created_at
FROM activity_versions v
JOIN activity_skills s ON s.activity_id = v.activity_id;

INSERT INTO question_version_skills (id, question_version_id, skill_id, created_at)
SELECT gen_random_uuid(), v.id, s.skill_id, s.created_at
FROM question_versions v
JOIN question_skills s ON s.question_id = v.question_id;

-- Published and retired versions' tags can't change, whatever writes to the database.
CREATE FUNCTION prevent_frozen_activity_version_skill_change()
RETURNS TRIGGER AS $$
DECLARE
    version_status VARCHAR(20);
BEGIN
    SELECT status INTO version_status FROM activity_versions
    WHERE id = COALESCE(NEW.activity_version_id, OLD.activity_version_id);
    IF version_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'skill tags of a % activity version are frozen; start a new version to change them', version_status;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_activity_version_skills_frozen
    BEFORE INSERT OR UPDATE OR DELETE ON activity_version_skills
    FOR EACH ROW
    EXECUTE FUNCTION prevent_frozen_activity_version_skill_change();

CREATE FUNCTION prevent_frozen_question_version_skill_change()
RETURNS TRIGGER AS $$
DECLARE
    version_status VARCHAR(20);
BEGIN
    SELECT status INTO version_status FROM question_versions
    WHERE id = COALESCE(NEW.question_version_id, OLD.question_version_id);
    IF version_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'skill tags of a % question version are frozen; start a new version to change them', version_status;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_question_version_skills_frozen
    BEFORE INSERT OR UPDATE OR DELETE ON question_version_skills
    FOR EACH ROW
    EXECUTE FUNCTION prevent_frozen_question_version_skill_change();

-- The identity-level tags are fully copied above; keeping them would leave two answers to "what does
-- this teach".
DROP TABLE activity_skills;
DROP TABLE question_skills;
