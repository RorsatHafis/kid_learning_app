-- Flexible plan management, slice 1: a plan's lineage.
--
-- An active plan is frozen (V42: its price and limits never change under a subscriber). The way to change what new
-- customers get is to duplicate it into a new draft, change the draft and activate it, then retire the old one.
-- based_on_plan_id records which plan a draft was copied from, so an administrator can see that "School 2027" is the
-- next version of "School 2026" and that both are the same offer at different times. Subscribers and payment orders
-- keep pointing at the exact plan (and plan version) they agreed to; nothing here moves them.

ALTER TABLE subscription_plans
    ADD COLUMN based_on_plan_id UUID;

ALTER TABLE subscription_plans
    ADD CONSTRAINT fk_subscription_plans_based_on FOREIGN KEY (based_on_plan_id) REFERENCES subscription_plans (id),
    ADD CONSTRAINT ck_subscription_plans_based_on_other CHECK (based_on_plan_id IS NULL OR based_on_plan_id <> id);

CREATE INDEX idx_subscription_plans_based_on ON subscription_plans (based_on_plan_id) WHERE based_on_plan_id IS NOT NULL;
