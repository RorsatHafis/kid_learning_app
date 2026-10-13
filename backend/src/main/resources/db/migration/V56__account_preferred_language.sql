-- The language an account uses the app in (UI only - it never changes which learning content a child
-- follows). English or Khmer; every existing account starts in English, the language it has always seen.
ALTER TABLE accounts
    ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'en',
    ADD CONSTRAINT ck_accounts_preferred_language CHECK (preferred_language IN ('en', 'km'));
