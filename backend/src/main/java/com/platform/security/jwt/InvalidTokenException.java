package com.platform.security.jwt;

/**
 * An access token that is malformed, expired or signed with another key.
 */
public final class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {

        super(message, cause);
        
    }
    
}
