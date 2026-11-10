-- Owner requirement 2026-11-10: a curriculum has its own age range (minimum and maximum age), set in the Studio.
--
-- Until now a curriculum's ages came from a fixed age group (age_hubs, V10). Every existing curriculum gets its age
-- group's range, so nothing a family sees changes with this migration. From now on the curriculum's own range is what
-- discovery and eligibility use; the age group stays only as an optional label of older curricula (a new curriculum
-- needs none). Ages are separate from school grades (grade_levels are untouched).

ALTER TABLE curriculums
    ADD COLUMN min_age INT,
    ADD COLUMN max_age INT;

UPDATE curriculums c SET min_age = h.min_age, max_age = h.max_age FROM age_hubs h WHERE h.id = c.age_hub_id;

ALTER TABLE curriculums
    ALTER COLUMN min_age SET NOT NULL,
    ALTER COLUMN age_hub_id DROP NOT NULL,
    ADD CONSTRAINT ck_curriculums_min_age CHECK (min_age >= 0),
    ADD CONSTRAINT ck_curriculums_age_range CHECK (max_age IS NULL OR max_age >= min_age);
