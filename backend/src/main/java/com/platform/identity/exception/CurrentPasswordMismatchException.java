package com.platform.identity.exception;

/**
 * The current password given to confirm an account change was wrong. Deliberately not
 * {@link InvalidCredentialsException}: that maps to 401, which means "not signed in" -
 * the caller here IS signed in, and a 401 would make clients refresh their session.
 */
public class CurrentPasswordMismatchException extends RuntimeException {

    public CurrentPasswordMismatchException() {
        super("The current password is incorrect");
    }

}
