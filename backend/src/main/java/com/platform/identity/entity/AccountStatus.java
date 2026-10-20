package com.platform.identity.entity;

/**
 * An account's state: active, disabled by an administrator, or (for a principal who registered) waiting for
 * approval.
 */
public enum AccountStatus {

    ACTIVE,
    DISABLED,
    PENDING_VERIFICATION
    
}
