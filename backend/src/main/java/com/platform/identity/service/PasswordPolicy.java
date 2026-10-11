package com.platform.identity.service;

import org.springframework.stereotype.Component;

import com.platform.identity.exception.WeakPasswordException;

@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /**
     * Passwords are hashed with BCrypt, which reads no more than 72 bytes (UTF-8); Spring refuses a longer
     * one. The limit is stated here, in words a person can act on, rather than surfacing as the encoder's
     * error - 72 letters, digits or symbols, fewer with letters beyond A-Z (a Khmer letter is 3 bytes).
     */
    public static final int MAX_BYTES = 72;
 
    public void validate(String rawPassword) {

        if (rawPassword == null || rawPassword.isEmpty()) {

            throw new WeakPasswordException("Password must not be blank");

        }

        if (rawPassword.length() < MIN_LENGTH) {

            throw new WeakPasswordException("Password must be at least " + MIN_LENGTH + " characters long");

        }

        if (rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {

            throw new WeakPasswordException("Password is too long: use at most " + MAX_BYTES
                    + " letters, digits or symbols (fewer if it has letters beyond A-Z, such as Khmer)");

        }

    }
    
}
