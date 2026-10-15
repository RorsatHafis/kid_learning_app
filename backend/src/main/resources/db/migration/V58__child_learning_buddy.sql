-- The Learning Buddy a child has chosen to walk their path with (UI only: it never changes what the
-- child learns, how answers are scored, or what a parent is told). Kept on the child, chosen in the
-- child's own space. Every child starts with Momo the otter until they choose.
ALTER TABLE children
    ADD COLUMN learning_buddy VARCHAR(20) NOT NULL DEFAULT 'OTTER',
    ADD CONSTRAINT ck_children_learning_buddy CHECK (learning_buddy IN ('OTTER', 'ELEPHANT', 'BUFFALO', 'GIBBON', 'FROG'));
