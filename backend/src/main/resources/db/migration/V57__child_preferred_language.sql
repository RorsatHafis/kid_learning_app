-- The language a child's own space is shown in (UI only - like an account's, it never changes which
-- learning content the child follows). Kept on the child, not taken from the parent: a child can read
-- Khmer while their parent uses English, and the reverse. Existing children start in English, the
-- language they have always seen; a new child starts in the language the parent is using.
ALTER TABLE children
    ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'en',
    ADD CONSTRAINT ck_children_preferred_language CHECK (preferred_language IN ('en', 'km'));
