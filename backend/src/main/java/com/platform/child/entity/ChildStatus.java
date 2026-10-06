package com.platform.child.entity;

public enum ChildStatus {

    ACTIVE,
    ARCHIVED,
    /** The family asked to delete the child; erased after the grace period unless kept (V51). */
    DELETION_PENDING
    
}
