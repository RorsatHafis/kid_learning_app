-- Deleting a child or a family account (Y3, PD-24 as adopted): a request schedules the
-- erasure after a grace period, during which it can be cancelled; then everything about
-- the child (or the family) is erased. Audit events are kept (they hold ids, no names).

-- A child awaiting erasure: out of every learning and school flow, still listed to its family.
ALTER TABLE children DROP CONSTRAINT ck_children_status;
ALTER TABLE children ADD CONSTRAINT ck_children_status
    CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETION_PENDING'));
ALTER TABLE children ADD COLUMN erase_after TIMESTAMPTZ;
ALTER TABLE children ADD CONSTRAINT ck_children_erase_after
    CHECK ((status = 'DELETION_PENDING') = (erase_after IS NOT NULL));
CREATE INDEX ix_children_erase_after ON children (erase_after) WHERE erase_after IS NOT NULL;

-- A family account whose deletion is scheduled (everything keeps working until then).
ALTER TABLE accounts ADD COLUMN erase_after TIMESTAMPTZ;
CREATE INDEX ix_accounts_erase_after ON accounts (erase_after) WHERE erase_after IS NOT NULL;

-- Evidence rows stay append-only for the application. The one exception is erasure: a
-- transaction that sets the local flag bookeyond.erasure may DELETE them (never UPDATE).
-- Audit events keep their own, stricter trigger.
CREATE OR REPLACE FUNCTION prevent_row_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('bookeyond.erasure', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION '% rows are immutable', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;
