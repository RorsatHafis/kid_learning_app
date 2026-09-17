ALTER TABLE accounts
    ADD COLUMN platform_role VARCHAR(20) NOT NULL DEFAULT 'PARENT';

ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_platform_role
    CHECK (platform_role IN ('PARENT', 'TEACHER', 'PRINCIPAL', 'ADMIN'));

CREATE INDEX ix_accounts_platform_role ON accounts (platform_role);
