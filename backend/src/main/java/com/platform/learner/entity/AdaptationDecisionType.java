package com.platform.learner.entity;

/** Matches {@code ck_adaptation_decisions_type} (V17). SCHEDULE_REVIEW is not recorded: Smart Review keeps its own state (review_items). */
public enum AdaptationDecisionType {
    CONTINUE,
    INCREASE_DIFFICULTY,
    REMEDIATE,
    SCHEDULE_REVIEW
}
