package com.platform.assessment.entity;

/** Mirrors the {@code status} CHECK constraint on {@code assessment_attempts} (V16 migration). */
public enum AssessmentAttemptStatus {

    IN_PROGRESS,
    COMPLETED,
    ABANDONED

}