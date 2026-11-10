package com.platform.identity.registration;

/** Owner requirement 2026-11-10: an account is created only with a verified email's registration grant. */
public class RegistrationNotVerifiedException extends RuntimeException {

    public RegistrationNotVerifiedException() {
        super("Confirm your email before creating an account");
    }
}
