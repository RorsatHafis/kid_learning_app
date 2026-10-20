package com.platform.identity.exception;

/**
 * Registration with an email that already has an account.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {

        super("An account already exists for this email address");
        
    }
    
}
