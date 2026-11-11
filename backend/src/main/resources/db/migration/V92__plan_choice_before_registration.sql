-- Owner requirement 2026-11-11: a visitor sees the plans and chooses one before registering.
-- The choice travels with the email verification (the server's record, never the browser's word) ...
ALTER TABLE registration_verifications ADD COLUMN chosen_plan_id UUID REFERENCES subscription_plans (id);

-- ... and, for a paid plan, is kept on the new account until it is paid for (the plan then starts from the confirmed
-- payment) or set aside for Free. A choice grants nothing: it only says which plan the checkout offers first.
CREATE TABLE plan_choices (
    account_id UUID PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    plan_id    UUID        NOT NULL REFERENCES subscription_plans (id),
    chosen_at  TIMESTAMPTZ NOT NULL
);
