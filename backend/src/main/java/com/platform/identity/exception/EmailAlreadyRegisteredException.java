package com.platform.identity.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {

        super("An account already exists for this email address");
        
    }
    
}
