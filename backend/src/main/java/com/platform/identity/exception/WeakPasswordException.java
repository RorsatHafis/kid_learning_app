package com.platform.identity.exception;

/**
 * A password that breaks the password policy; the message says which rule, in words a person can act on.
 */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException(String message) {

        super(message);
        
    }
    
}
