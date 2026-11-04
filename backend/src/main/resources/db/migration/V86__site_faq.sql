-- LAND-1 (owner direction 2026-11-04; settles L-3 for the landing page): the landing page's questions and answers,
-- written by administrators in English and Khmer, ordered and shown or hidden, like the site statements (V63). A
-- question and its answer are both required in both languages; nothing is shown that isn't written.
CREATE TABLE site_faq (
    id UUID PRIMARY KEY,
    question_en VARCHAR(200) NOT NULL,
    question_km VARCHAR(200) NOT NULL,
    answer_en VARCHAR(1000) NOT NULL,
    answer_km VARCHAR(1000) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_site_faq_text CHECK (char_length(btrim(question_en)) > 0 AND char_length(btrim(question_km)) > 0
        AND char_length(btrim(answer_en)) > 0 AND char_length(btrim(answer_km)) > 0),
    CONSTRAINT ck_site_faq_position CHECK (position >= 0),
    CONSTRAINT ck_site_faq_version CHECK (version >= 0)
);

CREATE INDEX ix_site_faq_visible ON site_faq (position, created_at) WHERE visible;

CREATE TRIGGER trg_site_faq_updated_at
    BEFORE UPDATE ON site_faq
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- First questions, answered only with what the product does today (administrators edit or hide them). Khmer is a
-- draft for native review (PD-14).
INSERT INTO site_faq (id, question_en, question_km, answer_en, answer_km, position, visible) VALUES
    ('5e7af000-0000-4000-8000-000000000001', 'Which ages is Bookeyond Kid for?', 'Bookeyond Kid សម្រាប់កុមារអាយុប៉ុន្មាន?',
     'Children aged 3 to 12. When you add a child, the subjects for their age are shown first - you can choose others too.',
     'កុមារអាយុពី 3 ដល់ 12 ឆ្នាំ។ ពេលអ្នកបន្ថែមកូន មុខវិជ្ជាសម្រាប់អាយុរបស់គេត្រូវបានបង្ហាញមុនគេ - អ្នកក៏អាចជ្រើសមុខវិជ្ជាផ្សេងទៀតបានដែរ។',
     1, TRUE),
    ('5e7af000-0000-4000-8000-000000000002', 'What does it cost?', 'តើវាតម្លៃប៉ុន្មាន?',
     'The plans on offer today are shown above, with their prices. Nothing is charged unless you choose a plan and pay for it.',
     'កញ្ចប់ដែលមានថ្ងៃនេះត្រូវបានបង្ហាញខាងលើ ជាមួយតម្លៃរបស់វា។ គ្មានការគិតប្រាក់ទេ លុះត្រាតែអ្នកជ្រើសកញ្ចប់មួយ ហើយបង់ប្រាក់សម្រាប់វា។',
     2, TRUE),
    ('5e7af000-0000-4000-8000-000000000003', 'Is it safe for my child?', 'តើវាមានសុវត្ថិភាពសម្រាប់កូនខ្ញុំទេ?',
     'There are no ads, no chat and no strangers. A grown-up holds a button to leave the child''s space, and your family''s learning data belongs to your family.',
     'គ្មានការផ្សាយពាណិជ្ជកម្ម គ្មានការជជែក និងគ្មានមនុស្សចម្លែកទេ។ មនុស្សធំសង្កត់ប៊ូតុងមួយឱ្យជាប់ ដើម្បីចាកចេញពីកន្លែងរបស់កុមារ ហើយទិន្នន័យការរៀនរបស់គ្រួសារអ្នក ជារបស់គ្រួសារអ្នក។',
     3, TRUE),
    ('5e7af000-0000-4000-8000-000000000004', 'What will I see as a parent?', 'តើខ្ញុំនឹងឃើញអ្វីខ្លះក្នុងនាមជាឪពុកម្ដាយ?',
     'What your child is learning, what their answers show and how sure that is, what changed in the last four weeks, and what comes next - with the reason.',
     'អ្វីដែលកូនអ្នកកំពុងរៀន អ្វីដែលចម្លើយរបស់គេបង្ហាញ និងភាពប្រាកដរបស់វា អ្វីដែលបានផ្លាស់ប្ដូរក្នុងរយៈពេលបួនសប្ដាហ៍ចុងក្រោយ និងអ្វីដែលនឹងមកបន្ទាប់ - ព្រមទាំងហេតុផល។',
     4, TRUE),
    ('5e7af000-0000-4000-8000-000000000005', 'Which languages does it use?', 'តើវាប្រើភាសាអ្វីខ្លះ?',
     'The app works in Khmer and English, and each child can have their own language. Lessons appear in a language as they are written for it.',
     'កម្មវិធីដំណើរការជាភាសាខ្មែរ និងភាសាអង់គ្លេស ហើយកូននីមួយៗអាចមានភាសាផ្ទាល់ខ្លួន។ មេរៀនបង្ហាញជាភាសាមួយ នៅពេលវាត្រូវបានសរសេរសម្រាប់ភាសានោះ។',
     5, TRUE),
    ('5e7af000-0000-4000-8000-000000000006', 'Can my child''s school use it?', 'តើសាលារបស់កូនខ្ញុំអាចប្រើវាបានទេ?',
     'Yes, in early access. A school sets up its classes and invites its teachers; families join a class with its code and can end that at any time.',
     'បាទ/ចាស ក្នុងដំណាក់កាលចូលប្រើដំបូង។ សាលារៀបចំថ្នាក់ និងអញ្ជើញគ្រូរបស់ខ្លួន ហើយគ្រួសារចូលរួមថ្នាក់មួយដោយប្រើលេខកូដរបស់វា ហើយអាចបញ្ចប់វាបានគ្រប់ពេល។',
     6, TRUE);
