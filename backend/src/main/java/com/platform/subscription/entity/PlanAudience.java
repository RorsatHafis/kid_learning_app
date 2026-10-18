package com.platform.subscription.entity;

/**
 * Who a plan is for (V59). A family plan sets what a family can add; a school plan sets what a
 * school can hold and how much media each of its teachers can keep. Each audience has its own
 * default plan, and a plan is only ever given to its own audience.
 */
public enum PlanAudience {
    FAMILY,
    SCHOOL
}
