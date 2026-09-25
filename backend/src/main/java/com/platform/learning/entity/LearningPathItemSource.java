package com.platform.learning.entity;

/**
 * Which subsystem placed this item on the path - mirrors the {@code source} CHECK
 * constraint on {@code learning_path_items} (V14 migration). Lets a future path view
 * show "why is this here" (base curriculum sequencing vs. an adaptive insertion vs.
 * a review obligation) without re-deriving it.
 */
public enum LearningPathItemSource {

    CURRICULUM,
    ADAPTIVE,
    REVIEW
    , TEACHER_ASSIGNED

}
