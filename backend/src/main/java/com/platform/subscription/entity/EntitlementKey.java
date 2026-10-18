package com.platform.subscription.entity;

import java.util.Arrays;
import java.util.List;

/**
 * The product's levers a plan can set (V42, V59 {@code ck_plan_entitlements_key}). Each is a limit
 * where {@code null} means unlimited, and each belongs to one audience. Learning evidence, history,
 * insights and a family's data export are deliberately not here: they are never locked (learning
 * integrity; the data belongs to the family).
 */
public enum EntitlementKey {
    /** How many children a family can add. */
    CHILDREN_MAX(PlanAudience.FAMILY),
    /** How many journeys (active or paused enrolments) a child can follow at once. */
    JOURNEYS_PER_CHILD_MAX(PlanAudience.FAMILY),
    /** How many children a school can have access to (each counted once, however many classes). */
    SCHOOL_CHILDREN_MAX(PlanAudience.SCHOOL),
    /** How many teachers a school can have on its staff (active, or invited and not yet answered). */
    SCHOOL_TEACHERS_MAX(PlanAudience.SCHOOL),
    /** How many classes a school can have in use (archived classes don't count). */
    SCHOOL_CLASSES_MAX(PlanAudience.SCHOOL),
    /** How much media (pictures and recordings) each of the school's teachers can keep, in MB. */
    TEACHER_STORAGE_MB(PlanAudience.SCHOOL);

    private final PlanAudience audience;

    EntitlementKey(PlanAudience audience) {
        this.audience = audience;
    }

    public PlanAudience audience() {
        return audience;
    }

    public static List<EntitlementKey> of(PlanAudience audience) {
        return Arrays.stream(values()).filter(key -> key.audience == audience).toList();
    }
}
