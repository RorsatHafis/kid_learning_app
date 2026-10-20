package com.platform.child.entity;

/**
 * A child's lifecycle: active, archived by the family (kept and restorable), or scheduled for erasure.
 */
public enum ChildStatus {

    ACTIVE,
    ARCHIVED,
    /** The family asked to delete the child; erased after the grace period unless kept (V51). */
    DELETION_PENDING
    
}
