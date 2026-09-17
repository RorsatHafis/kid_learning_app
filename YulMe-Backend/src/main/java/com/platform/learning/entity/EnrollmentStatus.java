package com.platform.learning.entity;

/** Mirrors the {@code status} CHECK constraint on {@code enrollments} (V14 migration). */
public enum EnrollmentStatus {

    ACTIVE,
    COMPLETED,
    PAUSED,
    WITHDRAWN

}