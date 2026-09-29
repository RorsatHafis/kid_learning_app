package com.platform.subscription.entity;

/**
 * The product's levers a plan can set (V42 {@code ck_plan_entitlements_key}). Each is a limit
 * where {@code null} means unlimited. Learning evidence, history, insights and a family's data
 * export are deliberately not here: they are never locked (learning integrity; the data belongs
 * to the family).
 */
public enum EntitlementKey {
    /** How many children a family can add. */
    CHILDREN_MAX,
    /** How many journeys (active or paused enrolments) a child can follow at once. */
    JOURNEYS_PER_CHILD_MAX
}
