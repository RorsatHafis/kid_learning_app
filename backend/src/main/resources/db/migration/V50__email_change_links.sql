-- Changing the sign-in email address (Y1). The link goes to the NEW address, so the
-- token must remember which address it proves; only an EMAIL_CHANGE token carries one.
ALTER TABLE account_action_tokens ADD COLUMN new_email VARCHAR(320);

ALTER TABLE account_action_tokens DROP CONSTRAINT ck_account_action_tokens_purpose;
ALTER TABLE account_action_tokens ADD CONSTRAINT ck_account_action_tokens_purpose
    CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ACCOUNT_SETUP', 'EMAIL_CHANGE'));

ALTER TABLE account_action_tokens ADD CONSTRAINT ck_account_action_tokens_new_email
    CHECK ((purpose = 'EMAIL_CHANGE') = (new_email IS NOT NULL));
