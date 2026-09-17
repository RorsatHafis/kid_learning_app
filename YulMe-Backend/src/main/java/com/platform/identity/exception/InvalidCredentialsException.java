package com.platform.identity.exception;

public final class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {

        super("Invalid email or password");
        
    }
    
}
