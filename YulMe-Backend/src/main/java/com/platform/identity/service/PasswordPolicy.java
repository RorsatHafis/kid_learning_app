package com.platform.identity.service;

import org.springframework.stereotype.Component;

import com.platform.identity.exception.WeakPasswordException;

@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
 
    public void validate(String rawPassword) {

        if (rawPassword == null || rawPassword.isEmpty()) {

            throw new WeakPasswordException("Password must not be blank");

        }

        if (rawPassword.length() < MIN_LENGTH) {

            throw new WeakPasswordException("Password must be at least " + MIN_LENGTH + " characters long");

        }

        if (rawPassword.length() > MAX_LENGTH) {

            throw new WeakPasswordException("Password must be at most " + MAX_LENGTH + " characters long");
            
        }

    }
    
}
