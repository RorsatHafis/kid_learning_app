-- Content in two languages (LOCALIZATION.md), plans: a plan's name and description in Khmer, beside the English.
--
-- Families and principals read a plan's words when they choose, ask for or pay for it; until now they were the
-- administrator's English whatever the reader's language. name_km / description_km are the administrator's own Khmer
-- (no automatic translation); NULL means not translated, and the reader gets the English. Like the English, they are
-- written while the plan is a draft and fixed once it is offered. Existing plans keep working exactly as before.

ALTER TABLE subscription_plans
    ADD COLUMN name_km VARCHAR(120),
    ADD COLUMN description_km VARCHAR(1000);

ALTER TABLE subscription_plans
    ADD CONSTRAINT ck_subscription_plans_name_km_not_blank
        CHECK (name_km IS NULL OR char_length(btrim(name_km)) > 0),
    ADD CONSTRAINT ck_subscription_plans_description_km_not_blank
        CHECK (description_km IS NULL OR char_length(btrim(description_km)) > 0);
