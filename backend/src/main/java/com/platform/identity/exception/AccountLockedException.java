package com.platform.identity.exception;

import java.time.Instant;

/**
 * Too many failed sign-ins: the account is locked until the given time (answered as 423 with that time).
 */
public final class AccountLockedException extends RuntimeException {

    private final Instant lockedUntil;
 
    public AccountLockedException(Instant lockedUntil) {

        super("Account is temporarily locked");
        this.lockedUntil = lockedUntil;

    }
 
    public Instant getLockedUntil() {

        return lockedUntil;
        
    }
    
}
