-- Every refresh session belongs to one family: the chain of sessions descended from a
-- single login or registration. Reuse detection revokes the whole family.
--
-- Until now the family was found by walking replaced_by_session_id links, one query per
-- link (TD-05), and that walk breaks wherever a link is cleared or re-pointed. A lost
-- refresh response recovered within the reuse grace window (PD-03, N19) re-points the
-- rotated session at a new successor, so the family must be recorded explicitly.
ALTER TABLE account_sessions ADD COLUMN family_id UUID;

-- Backfill: each chain root (a session nobody replaced into) starts a family and every
-- descendant reached through replaced_by_session_id joins it.
WITH RECURSIVE chain AS (
    SELECT s.id, s.id AS family_id
      FROM account_sessions s
     WHERE NOT EXISTS (SELECT 1 FROM account_sessions p WHERE p.replaced_by_session_id = s.id)
    UNION ALL
    SELECT successor.id, chain.family_id
      FROM chain
      JOIN account_sessions current ON current.id = chain.id
      JOIN account_sessions successor ON successor.id = current.replaced_by_session_id
)
UPDATE account_sessions s
   SET family_id = chain.family_id
  FROM chain
 WHERE chain.id = s.id;

-- Only a cyclic chain, which the rotation rules cannot produce, would be left over; each
-- such row becomes its own family.
UPDATE account_sessions SET family_id = id WHERE family_id IS NULL;

ALTER TABLE account_sessions ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX ix_account_sessions_family_status ON account_sessions (family_id, status);
