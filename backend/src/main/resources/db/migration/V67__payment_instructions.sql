-- M8b (product improvement program): how families and schools pay by bank transfer is set by an administrator in the
-- app (in English and Khmer, with an optional KHQR image from the owner's own bank), instead of only through the
-- PAYMENTS_MANUAL_* environment variables, which remain the fallback until the first version is saved.
--
-- Append-only: every change is a new version, and each order keeps the version it was started with, so changing the
-- instructions never rewrites an invoice already issued. Saving instructions never confirms or grants anything.

CREATE TABLE payment_instruction_versions (
    id UUID PRIMARY KEY,
    method VARCHAR(20) NOT NULL,
    version INT NOT NULL,
    enabled BOOLEAN NOT NULL,
    instructions_en TEXT,
    instructions_km TEXT,
    -- An image the platform keeps (content_assets, owner null); in use, it can't be deleted.
    qr_asset_id UUID,
    created_by_account_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_payment_instruction_versions UNIQUE (method, version),
    CONSTRAINT fk_payment_instruction_versions_qr FOREIGN KEY (qr_asset_id) REFERENCES content_assets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_instruction_versions_method CHECK (method IN ('MANUAL')),
    CONSTRAINT ck_payment_instruction_versions_version CHECK (version > 0),
    CONSTRAINT ck_payment_instruction_versions_enabled CHECK (NOT enabled OR length(btrim(coalesce(instructions_en, ''))) > 0),
    CONSTRAINT ck_payment_instruction_versions_length CHECK (length(coalesce(instructions_en, '')) <= 2000
        AND length(coalesce(instructions_km, '')) <= 2000)
);

-- The instructions an order was started with (null: started before M8b, or from the environment's).
ALTER TABLE payment_orders ADD COLUMN instructions_version_id UUID;
ALTER TABLE payment_orders
    ADD CONSTRAINT fk_payment_orders_instructions FOREIGN KEY (instructions_version_id)
        REFERENCES payment_instruction_versions (id) ON DELETE RESTRICT;
