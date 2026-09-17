package com.platform.security.jwt;

public final class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {

        super(message, cause);
        
    }
    
}
