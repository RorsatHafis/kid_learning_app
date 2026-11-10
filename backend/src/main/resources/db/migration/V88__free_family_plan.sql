-- Owner requirement 2026-11-10: a family without a subscription is on a free plan - one child, up to three learning
-- options (curricula) for that child, one of them active at a time; the family switches between them.
--
-- 1. A new limit, ACTIVE_JOURNEYS_PER_CHILD_MAX: how many of a child's journeys can be active at once (the others
--    wait, paused, until the family switches). NULL is unlimited, like every limit.
-- 2. A new FAMILY plan holding the owner's limits becomes the families' default. An offered plan is frozen (V42, V79:
--    its limits never change under anyone), so "Early access" is not edited: it stays an active plan the administrator
--    can still grant (it was never self-service, so families are not offered it), and anyone it was granted to keeps it.
--
-- Nothing a family has is removed or changed: limits apply only when a family adds a child or starts / switches a
-- journey. A family already over a limit keeps what it has (a child is never deleted, a journey never stopped or
-- paused by this migration).

ALTER TABLE plan_entitlements DROP CONSTRAINT ck_plan_entitlements_key;
ALTER TABLE plan_entitlements ADD CONSTRAINT ck_plan_entitlements_key CHECK (entitlement_key IN (
    'CHILDREN_MAX', 'JOURNEYS_PER_CHILD_MAX', 'ACTIVE_JOURNEYS_PER_CHILD_MAX',
    'SCHOOL_CHILDREN_MAX', 'SCHOOL_TEACHERS_MAX', 'SCHOOL_CLASSES_MAX', 'TEACHER_STORAGE_MB'));

-- Every existing family plan keeps exactly what it allowed: unlimited active journeys.
INSERT INTO plan_entitlements (id, plan_id, entitlement_key, limit_value)
SELECT gen_random_uuid(), p.id, 'ACTIVE_JOURNEYS_PER_CHILD_MAX', NULL
FROM subscription_plans p
WHERE p.audience = 'FAMILY'
  AND NOT EXISTS (SELECT 1 FROM plan_entitlements e WHERE e.plan_id = p.id AND e.entitlement_key = 'ACTIVE_JOURNEYS_PER_CHILD_MAX');

UPDATE subscription_plans SET is_default = FALSE WHERE audience = 'FAMILY' AND is_default;

INSERT INTO subscription_plans (id, code, name, description, name_km, description_km, status, is_default, self_service, audience)
VALUES ('6f1c2a60-0000-4000-8000-000000000088', 'FAMILY_FREE_2026_11', 'Free',
        'For every family without a subscription: one child, up to three subjects, learning one of them at a time.',
        'ឥតគិតថ្លៃ',
        'សម្រាប់គ្រួសារទាំងអស់ដែលមិនទាន់ជាវ៖ កូនម្នាក់ មុខវិជ្ជារហូតដល់បី ហើយរៀនម្ដងមួយមុខវិជ្ជា។',
        'ACTIVE', TRUE, FALSE, 'FAMILY');

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, limit_value) VALUES
    ('6f1c2a60-0000-4000-8000-000000000089', '6f1c2a60-0000-4000-8000-000000000088', 'CHILDREN_MAX', 1),
    ('6f1c2a60-0000-4000-8000-00000000008a', '6f1c2a60-0000-4000-8000-000000000088', 'JOURNEYS_PER_CHILD_MAX', 3),
    ('6f1c2a60-0000-4000-8000-00000000008b', '6f1c2a60-0000-4000-8000-000000000088', 'ACTIVE_JOURNEYS_PER_CHILD_MAX', 1);
