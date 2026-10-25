-- M9b (owner decision D-15, 2026-10-25): email stays required on every account, and a code texted to the account's
-- verified phone can also reset a forgotten password. Codes now say what they are for; every existing code verified a
-- number.
ALTER TABLE phone_verifications
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'VERIFY',
    ADD CONSTRAINT ck_phone_verifications_purpose CHECK (purpose IN ('VERIFY', 'RESET'));
