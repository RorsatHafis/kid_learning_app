package com.platform.subscription.service;

import com.platform.subscription.entity.EntitlementKey;

/** A family or a school tried to add something its plan doesn't allow (409 with the entitlement and its limit). */
public class PlanLimitException extends RuntimeException {

    private final EntitlementKey entitlement;
    private final int limit;

    public PlanLimitException(EntitlementKey entitlement, int limit) {
        super(switch (entitlement) {
            case CHILDREN_MAX -> limit == 1 ? "Your family's plan includes one child." : "Your family's plan includes up to " + limit + " children.";
            case JOURNEYS_PER_CHILD_MAX -> limit == 1 ? "Your family's plan includes one journey per child at a time."
                    : "Your family's plan includes up to " + limit + " journeys per child at a time.";
            case SCHOOL_CHILDREN_MAX -> limit == 1 ? "Your school's plan includes one child." : "Your school's plan includes up to " + limit + " children.";
            case SCHOOL_TEACHERS_MAX -> limit == 1 ? "Your school's plan includes one teacher." : "Your school's plan includes up to " + limit + " teachers.";
            case SCHOOL_CLASSES_MAX -> limit == 1 ? "Your school's plan includes one class in use."
                    : "Your school's plan includes up to " + limit + " classes in use.";
            case TEACHER_STORAGE_MB -> "Your school's plan includes " + limit + " MB of pictures and recordings per teacher.";
        });
        this.entitlement = entitlement;
        this.limit = limit;
    }

    public EntitlementKey getEntitlement() {
        return entitlement;
    }

    public int getLimit() {
        return limit;
    }
}
