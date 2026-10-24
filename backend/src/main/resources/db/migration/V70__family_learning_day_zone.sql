-- M3 follow-up (product improvement program, family-first pass): the time zone a family's learning day follows -
-- when today's learning time starts again at midnight. Null is the platform's (platform.learning.day-zone, Cambodia
-- by default), so every existing family keeps exactly the day it had. Set by a parent; validated as an IANA zone id
-- by the server before it is stored.
ALTER TABLE families
    ADD COLUMN learning_day_zone VARCHAR(64),
    ADD CONSTRAINT ck_families_learning_day_zone CHECK (learning_day_zone IS NULL OR char_length(btrim(learning_day_zone)) > 0);
