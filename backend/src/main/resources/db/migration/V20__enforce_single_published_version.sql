CREATE UNIQUE INDEX uq_curriculum_versions_one_published
    ON curriculum_versions (curriculum_id)
    WHERE status = 'PUBLISHED';

CREATE UNIQUE INDEX uq_lesson_versions_one_published
    ON lesson_versions (lesson_id)
    WHERE status = 'PUBLISHED';

CREATE UNIQUE INDEX uq_activity_versions_one_published
    ON activity_versions (activity_id)
    WHERE status = 'PUBLISHED';

CREATE UNIQUE INDEX uq_question_versions_one_published
    ON question_versions (question_id)
    WHERE status = 'PUBLISHED';

CREATE UNIQUE INDEX uq_assessment_versions_one_published
    ON assessment_versions (assessment_id)
    WHERE status = 'PUBLISHED';
