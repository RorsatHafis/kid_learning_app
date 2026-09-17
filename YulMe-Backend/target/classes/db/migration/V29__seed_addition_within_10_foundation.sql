
INSERT INTO subjects (id, code, name, description, status)
VALUES ('00000000-add1-4000-a000-000000000001', 'MATHS', 'Maths', 'Foundational mathematics.', 'ACTIVE');

INSERT INTO age_hubs (id, code, name, min_age, max_age, display_order)
VALUES ('00000000-add1-4000-a000-000000000002', 'JUNIOR', 'Junior', 4, 9, 1);

INSERT INTO curriculums (id, subject_id, age_hub_id, name, status)
VALUES ('00000000-add1-4000-a000-000000000003', '00000000-add1-4000-a000-000000000001', '00000000-add1-4000-a000-000000000002', 'Maths - Junior', 'ACTIVE');

INSERT INTO curriculum_versions (id, curriculum_id, version_number, status, published_at)
VALUES ('00000000-add1-4000-a000-000000000004', '00000000-add1-4000-a000-000000000003', 1, 'PUBLISHED', now());

INSERT INTO skills (id, subject_id, code, name, description, status)
VALUES ('00000000-add1-4000-a000-000000000005', '00000000-add1-4000-a000-000000000001', 'MATHS_ADD_RECOGNITION', 'Recognize addition',
        'Recognizes an addition expression and what it is asking for.', 'ACTIVE');

INSERT INTO skills (id, subject_id, code, name, description, status)
VALUES ('00000000-add1-4000-a000-000000000006', '00000000-add1-4000-a000-000000000001', 'MATHS_ADD_WITHIN_5', 'Add within 5',
        'Adds two numbers whose sum is 5 or less.', 'ACTIVE');

INSERT INTO skills (id, subject_id, code, name, description, status)
VALUES ('00000000-add1-4000-a000-000000000007', '00000000-add1-4000-a000-000000000001', 'MATHS_ADD_WITHIN_10', 'Add within 10',
        'Adds two numbers whose sum is 10 or less.', 'ACTIVE');

INSERT INTO skills (id, subject_id, code, name, description, status)
VALUES ('00000000-add1-4000-a000-000000000008', '00000000-add1-4000-a000-000000000001', 'MATHS_ADD_FLUENCY_10', 'Addition fluency within 10',
        'Answers addition-within-10 problems quickly and reliably.', 'ACTIVE');

INSERT INTO skill_prerequisites (id, skill_id, prerequisite_skill_id)
VALUES ('00000000-add1-4000-a000-000000000009', '00000000-add1-4000-a000-000000000006', '00000000-add1-4000-a000-000000000005');

INSERT INTO skill_prerequisites (id, skill_id, prerequisite_skill_id)
VALUES ('00000000-add1-4000-a000-00000000000a', '00000000-add1-4000-a000-000000000007', '00000000-add1-4000-a000-000000000006');

INSERT INTO skill_prerequisites (id, skill_id, prerequisite_skill_id)
VALUES ('00000000-add1-4000-a000-00000000000b', '00000000-add1-4000-a000-000000000008', '00000000-add1-4000-a000-000000000007');

INSERT INTO learning_objectives (id, subject_id, code, title, description, status)
VALUES ('00000000-add1-4000-a000-00000000000c', '00000000-add1-4000-a000-000000000001', 'OBJ_ADDITION_WITHIN_10', 'Addition Within 10',
        'The learner can recognize, perform, and apply addition with sums up to 10.', 'ACTIVE');

INSERT INTO learning_objective_skills (id, learning_objective_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000000d', '00000000-add1-4000-a000-00000000000c', '00000000-add1-4000-a000-000000000005');

INSERT INTO learning_objective_skills (id, learning_objective_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000000e', '00000000-add1-4000-a000-00000000000c', '00000000-add1-4000-a000-000000000006');

INSERT INTO learning_objective_skills (id, learning_objective_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000000f', '00000000-add1-4000-a000-00000000000c', '00000000-add1-4000-a000-000000000007');

INSERT INTO learning_objective_skills (id, learning_objective_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000010', '00000000-add1-4000-a000-00000000000c', '00000000-add1-4000-a000-000000000008');

INSERT INTO curriculum_objectives (id, curriculum_version_id, learning_objective_id, display_order)
VALUES ('00000000-add1-4000-a000-000000000011', '00000000-add1-4000-a000-000000000004', '00000000-add1-4000-a000-00000000000c', 1);

INSERT INTO lessons (id, subject_id, title, status)
VALUES ('00000000-add1-4000-a000-000000000012', '00000000-add1-4000-a000-000000000001', 'Addition Within 10', 'ACTIVE');

INSERT INTO lesson_versions (id, lesson_id, version_number, status, body, published_at)
VALUES ('00000000-add1-4000-a000-000000000013', '00000000-add1-4000-a000-000000000012', 1, 'PUBLISHED',
        'A foundation Maths lesson: recognizing addition, then adding within 5, within 10, and building fluency.',
        now());

INSERT INTO lesson_skills (id, lesson_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000014', '00000000-add1-4000-a000-000000000012', '00000000-add1-4000-a000-000000000005');

INSERT INTO lesson_skills (id, lesson_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000015', '00000000-add1-4000-a000-000000000012', '00000000-add1-4000-a000-000000000006');

INSERT INTO lesson_skills (id, lesson_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000016', '00000000-add1-4000-a000-000000000012', '00000000-add1-4000-a000-000000000007');

INSERT INTO lesson_skills (id, lesson_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000017', '00000000-add1-4000-a000-000000000012', '00000000-add1-4000-a000-000000000008');

INSERT INTO activities (id, subject_id, lesson_id, activity_type, title, status)
VALUES ('00000000-add1-4000-a000-000000000018', '00000000-add1-4000-a000-000000000001', '00000000-add1-4000-a000-000000000012', 'PRACTICE', 'Addition Recognition Practice', 'ACTIVE');

INSERT INTO activity_versions (id, activity_id, version_number, status, instructions,
                             difficulty_level, estimated_duration_seconds, published_at)
VALUES ('00000000-add1-4000-a000-000000000019', '00000000-add1-4000-a000-000000000018', 1, 'PUBLISHED', 'Solve each addition problem.', 1, 60, now());

INSERT INTO activity_skills (id, activity_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000001a', '00000000-add1-4000-a000-000000000018', '00000000-add1-4000-a000-000000000005');

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-00000000001b', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-00000000001c', '00000000-add1-4000-a000-00000000001b', 1, 'PUBLISHED', '1 + 1 = ?', '2', 1, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000001d', '00000000-add1-4000-a000-00000000001b', '00000000-add1-4000-a000-000000000005');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-00000000001e', '00000000-add1-4000-a000-000000000019', '00000000-add1-4000-a000-00000000001c', 1, 1);

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-00000000001f', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-000000000020', '00000000-add1-4000-a000-00000000001f', 1, 'PUBLISHED', '2 + 1 = ?', '3', 1, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000021', '00000000-add1-4000-a000-00000000001f', '00000000-add1-4000-a000-000000000005');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-000000000022', '00000000-add1-4000-a000-000000000019', '00000000-add1-4000-a000-000000000020', 2, 1);

INSERT INTO activities (id, subject_id, lesson_id, activity_type, title, status)
VALUES ('00000000-add1-4000-a000-000000000023', '00000000-add1-4000-a000-000000000001', '00000000-add1-4000-a000-000000000012', 'PRACTICE', 'Addition Within 5 Practice', 'ACTIVE');

INSERT INTO activity_versions (id, activity_id, version_number, status, instructions,
                             difficulty_level, estimated_duration_seconds, published_at)
VALUES ('00000000-add1-4000-a000-000000000024', '00000000-add1-4000-a000-000000000023', 1, 'PUBLISHED', 'Solve each addition problem.', 2, 60, now());

INSERT INTO activity_skills (id, activity_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000025', '00000000-add1-4000-a000-000000000023', '00000000-add1-4000-a000-000000000006');

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-000000000026', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-000000000027', '00000000-add1-4000-a000-000000000026', 1, 'PUBLISHED', '2 + 3 = ?', '5', 2, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000028', '00000000-add1-4000-a000-000000000026', '00000000-add1-4000-a000-000000000006');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-000000000029', '00000000-add1-4000-a000-000000000024', '00000000-add1-4000-a000-000000000027', 1, 1);

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-00000000002a', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-00000000002b', '00000000-add1-4000-a000-00000000002a', 1, 'PUBLISHED', '1 + 4 = ?', '5', 2, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000002c', '00000000-add1-4000-a000-00000000002a', '00000000-add1-4000-a000-000000000006');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-00000000002d', '00000000-add1-4000-a000-000000000024', '00000000-add1-4000-a000-00000000002b', 2, 1);

INSERT INTO activities (id, subject_id, lesson_id, activity_type, title, status)
VALUES ('00000000-add1-4000-a000-00000000002e', '00000000-add1-4000-a000-000000000001', '00000000-add1-4000-a000-000000000012', 'PRACTICE', 'Addition Within 10 Practice - Easy', 'ACTIVE');

INSERT INTO activity_versions (id, activity_id, version_number, status, instructions,
                             difficulty_level, estimated_duration_seconds, published_at)
VALUES ('00000000-add1-4000-a000-00000000002f', '00000000-add1-4000-a000-00000000002e', 1, 'PUBLISHED', 'Solve each addition problem.', 2, 60, now());

INSERT INTO activity_skills (id, activity_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000030', '00000000-add1-4000-a000-00000000002e', '00000000-add1-4000-a000-000000000007');

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-000000000031', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-000000000032', '00000000-add1-4000-a000-000000000031', 1, 'PUBLISHED', '3 + 4 = ?', '7', 2, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000033', '00000000-add1-4000-a000-000000000031', '00000000-add1-4000-a000-000000000007');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-000000000034', '00000000-add1-4000-a000-00000000002f', '00000000-add1-4000-a000-000000000032', 1, 1);

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-000000000035', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-000000000036', '00000000-add1-4000-a000-000000000035', 1, 'PUBLISHED', '5 + 4 = ?', '9', 2, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000037', '00000000-add1-4000-a000-000000000035', '00000000-add1-4000-a000-000000000007');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-000000000038', '00000000-add1-4000-a000-00000000002f', '00000000-add1-4000-a000-000000000036', 2, 1);

INSERT INTO activities (id, subject_id, lesson_id, activity_type, title, status)
VALUES ('00000000-add1-4000-a000-000000000039', '00000000-add1-4000-a000-000000000001', '00000000-add1-4000-a000-000000000012', 'PRACTICE', 'Addition Within 10 Practice - Medium', 'ACTIVE');

INSERT INTO activity_versions (id, activity_id, version_number, status, instructions,
                             difficulty_level, estimated_duration_seconds, published_at)
VALUES ('00000000-add1-4000-a000-00000000003a', '00000000-add1-4000-a000-000000000039', 1, 'PUBLISHED', 'Solve each addition problem.', 5, 60, now());

INSERT INTO activity_skills (id, activity_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000003b', '00000000-add1-4000-a000-000000000039', '00000000-add1-4000-a000-000000000007');

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-00000000003c', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-00000000003d', '00000000-add1-4000-a000-00000000003c', 1, 'PUBLISHED', '6 + 3 = ?', '9', 5, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000003e', '00000000-add1-4000-a000-00000000003c', '00000000-add1-4000-a000-000000000007');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-00000000003f', '00000000-add1-4000-a000-00000000003a', '00000000-add1-4000-a000-00000000003d', 1, 1);

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-000000000040', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-000000000041', '00000000-add1-4000-a000-000000000040', 1, 'PUBLISHED', '4 + 5 = ?', '9', 5, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000042', '00000000-add1-4000-a000-000000000040', '00000000-add1-4000-a000-000000000007');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-000000000043', '00000000-add1-4000-a000-00000000003a', '00000000-add1-4000-a000-000000000041', 2, 1);

INSERT INTO activities (id, subject_id, lesson_id, activity_type, title, status)
VALUES ('00000000-add1-4000-a000-000000000044', '00000000-add1-4000-a000-000000000001', '00000000-add1-4000-a000-000000000012', 'PRACTICE', 'Addition Within 10 Practice - Hard', 'ACTIVE');

INSERT INTO activity_versions (id, activity_id, version_number, status, instructions,
                             difficulty_level, estimated_duration_seconds, published_at)
VALUES ('00000000-add1-4000-a000-000000000045', '00000000-add1-4000-a000-000000000044', 1, 'PUBLISHED', 'Solve each addition problem.', 9, 60, now());

INSERT INTO activity_skills (id, activity_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000046', '00000000-add1-4000-a000-000000000044', '00000000-add1-4000-a000-000000000007');

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-000000000047', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-000000000048', '00000000-add1-4000-a000-000000000047', 1, 'PUBLISHED', '7 + 3 = ?', '10', 9, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000049', '00000000-add1-4000-a000-000000000047', '00000000-add1-4000-a000-000000000007');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-00000000004a', '00000000-add1-4000-a000-000000000045', '00000000-add1-4000-a000-000000000048', 1, 1);

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-00000000004b', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-00000000004c', '00000000-add1-4000-a000-00000000004b', 1, 'PUBLISHED', '8 + 2 = ?', '10', 9, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-00000000004d', '00000000-add1-4000-a000-00000000004b', '00000000-add1-4000-a000-000000000007');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-00000000004e', '00000000-add1-4000-a000-000000000045', '00000000-add1-4000-a000-00000000004c', 2, 1);

INSERT INTO activities (id, subject_id, lesson_id, activity_type, title, status)
VALUES ('00000000-add1-4000-a000-00000000004f', '00000000-add1-4000-a000-000000000001', '00000000-add1-4000-a000-000000000012', 'CHALLENGE', 'Addition Fluency Challenge', 'ACTIVE');

INSERT INTO activity_versions (id, activity_id, version_number, status, instructions,
                             difficulty_level, estimated_duration_seconds, published_at)
VALUES ('00000000-add1-4000-a000-000000000050', '00000000-add1-4000-a000-00000000004f', 1, 'PUBLISHED', 'Solve each addition problem.', 6, 60, now());

INSERT INTO activity_skills (id, activity_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000051', '00000000-add1-4000-a000-00000000004f', '00000000-add1-4000-a000-000000000008');

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-000000000052', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-000000000053', '00000000-add1-4000-a000-000000000052', 1, 'PUBLISHED', '6 + 4 = ?', '10', 6, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000054', '00000000-add1-4000-a000-000000000052', '00000000-add1-4000-a000-000000000008');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-000000000055', '00000000-add1-4000-a000-000000000050', '00000000-add1-4000-a000-000000000053', 1, 1);

INSERT INTO questions (id, subject_id, question_type, status)
VALUES ('00000000-add1-4000-a000-000000000056', '00000000-add1-4000-a000-000000000001', 'NUMERIC', 'ACTIVE');

INSERT INTO question_versions (id, question_id, version_number, status, prompt,
                                 correct_answer, difficulty_level, published_at)
VALUES ('00000000-add1-4000-a000-000000000057', '00000000-add1-4000-a000-000000000056', 1, 'PUBLISHED', '9 + 1 = ?', '10', 6, now());

INSERT INTO question_skills (id, question_id, skill_id)
VALUES ('00000000-add1-4000-a000-000000000058', '00000000-add1-4000-a000-000000000056', '00000000-add1-4000-a000-000000000008');

INSERT INTO activity_items (id, activity_version_id, question_version_id, sequence_order, points)
VALUES ('00000000-add1-4000-a000-000000000059', '00000000-add1-4000-a000-000000000050', '00000000-add1-4000-a000-000000000057', 2, 1);
