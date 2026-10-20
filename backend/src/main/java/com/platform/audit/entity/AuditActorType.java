package com.platform.audit.entity;

/**
 * Who performed an audited action: a role, or the system itself (sweeps, startup).
 */
public enum AuditActorType {

    PARENT,
    CHILD,
    TEACHER,
    PRINCIPAL,
    ADMIN,
    SYSTEM
    
}
