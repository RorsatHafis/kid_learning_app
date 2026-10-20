package com.platform.identity.service;

import org.springframework.stereotype.Component;

import com.platform.identity.exception.WeakPasswordException;

/**
 * The password rules: at least 12 characters, within bcrypt's limit, not a repeated pattern, a sequence or a
 * commonly used password, and not the email itself.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /**
     * Passwords are hashed with BCrypt, which reads no more than 72 bytes (UTF-8); Spring refuses a longer
     * one. The limit is stated here, in words a person can act on, rather than surfacing as the encoder's
     * error - 72 letters, digits or symbols, fewer with letters beyond A-Z (a Khmer letter is 3 bytes).
     */
    public static final int MAX_BYTES = 72;
 
    /** Keyboard runs and counting a person might type to make up the length. */
    private static final java.util.List<String> SEQUENCES = java.util.List.of(
            "01234567890123456789012345678901234567890", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz",
            "qwertyuiopasdfghjklzxcvbnmqwertyuiop", "1234567890qwertyuiop");

    /** The service's own name and the word itself: the first guesses anyone tries (NIST 800-63B 5.1.1.2). */
    private static final java.util.Set<String> CONTEXT_WORDS = java.util.Set.of(
            "password", "passwords", "bookeyond", "bookeyondkid", "bookeyondkids", "qwerty", "letmein", "welcome");

    public void validate(String rawPassword) {
        validate(rawPassword, null);
    }

    /**
     * Length (12 characters to 72 bytes), and none of what is guessed first (NIST 800-63B 5.1.1.2): one
     * character repeated, a short pattern repeated, counting or a keyboard run, the service's name or the
     * word "password" with digits and symbols around it, or the account's own email address.
     */
    public void validate(String rawPassword, String email) {

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

        String lower = rawPassword.toLowerCase(java.util.Locale.ROOT);
        if (repeatsAShortPattern(lower)) {
            throw new WeakPasswordException("That password is a few characters repeated: choose one that is harder to guess");
        }
        for (String sequence : SEQUENCES) {
            if (sequence.contains(lower) || new StringBuilder(sequence).reverse().toString().contains(lower)) {
                throw new WeakPasswordException("That password is counting or a row of the keyboard: choose one that is harder to guess");
            }
        }
        String letters = lower.replaceAll("[^\\p{L}]", "");
        if (CONTEXT_WORDS.contains(letters)) {
            throw new WeakPasswordException("That password is one of the first anyone would try: choose one that is harder to guess");
        }
        if (email != null) {
            String local = email.toLowerCase(java.util.Locale.ROOT).split("@", 2)[0];
            String localLetters = local.replaceAll("[^\\p{L}]", "");
            if (lower.contains(email.toLowerCase(java.util.Locale.ROOT)) || (localLetters.length() >= 4 && letters.equals(localLetters))) {
                throw new WeakPasswordException("Don't use your email address in your password");
            }
        }

    }

    /** True when the whole password is a pattern of at most three characters said again and again ("abcabcabcabc"). */
    private static boolean repeatsAShortPattern(String password) {
        for (int period = 1; period <= 3; period++) {
            if (password.length() % period == 0 && password.equals(password.substring(0, period).repeat(password.length() / period))) {
                return true;
            }
        }
        return false;
    }

}
