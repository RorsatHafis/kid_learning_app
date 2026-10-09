-- Which version of the terms of use and privacy notice an account agreed to, and when (Y15, PD-18).
-- Null while no legal documents are configured, or for accounts created before they were.
ALTER TABLE accounts ADD COLUMN legal_version_accepted VARCHAR(40);
ALTER TABLE accounts ADD COLUMN legal_accepted_at TIMESTAMPTZ;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_legal_acceptance
    CHECK ((legal_version_accepted IS NULL) = (legal_accepted_at IS NULL));
