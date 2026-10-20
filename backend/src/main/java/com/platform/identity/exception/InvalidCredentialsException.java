package com.platform.identity.exception;

/**
 * Wrong email or password. Deliberately the same for both, so sign-in doesn't reveal which emails exist.
 */
public final class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {

        super("Invalid email or password");
        
    }
    
}
