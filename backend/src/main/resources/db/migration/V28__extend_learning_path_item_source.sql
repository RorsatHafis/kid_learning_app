ALTER TABLE learning_path_items
    DROP CONSTRAINT ck_learning_path_items_source;

ALTER TABLE learning_path_items
    ADD CONSTRAINT ck_learning_path_items_source
    CHECK (source IN ('CURRICULUM', 'ADAPTIVE', 'REVIEW', 'TEACHER_ASSIGNED'));
