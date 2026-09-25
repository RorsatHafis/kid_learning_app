package com.platform.learning.entity;

/** Mirrors the {@code status} CHECK constraint on {@code learning_path_items} (V14 migration). */
public enum LearningPathItemStatus {

    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED

}