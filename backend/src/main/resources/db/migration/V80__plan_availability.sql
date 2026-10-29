-- Flexible plan management, slice 2: when a plan is offered to new customers.
--
-- available_from / available_until bound the time an ACTIVE plan can be taken up by someone new: chosen or asked for
-- by a family, chosen by a school, given or invoiced by an administrator. Outside the window nobody new gets it.
-- Whoever is already on the plan is unaffected (their subscription, its period and renewals of the same plan go on),
-- because availability is about the offer, not about what a subscriber agreed to. NULL means no bound on that side.
-- The default plan is what everyone without a subscription has, so it is always offered and has no window.

ALTER TABLE subscription_plans
    ADD COLUMN available_from TIMESTAMPTZ,
    ADD COLUMN available_until TIMESTAMPTZ;

ALTER TABLE subscription_plans
    ADD CONSTRAINT ck_subscription_plans_availability
        CHECK (available_from IS NULL OR available_until IS NULL OR available_until > available_from),
    ADD CONSTRAINT ck_subscription_plans_default_always_offered
        CHECK (NOT is_default OR (available_from IS NULL AND available_until IS NULL));
