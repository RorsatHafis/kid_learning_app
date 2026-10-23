package com.platform.security.stepup;

/**
 * M9a: a step-up (or setup) needs a code from the authenticator app: none given, a wrong or reused one (answered 422
 * with {@code secondFactorRequired}), or, for an administrator without one, an app to be turned on first (403 with
 * {@code secondFactorSetupRequired}).
 */
public class SecondFactorMismatchException extends RuntimeException {

    private final boolean setupRequired;

    public SecondFactorMismatchException(String message) {
        this(message, false);
    }

    private SecondFactorMismatchException(String message, boolean setupRequired) {
        super(message);
        this.setupRequired = setupRequired;
    }

    static SecondFactorMismatchException setupRequired(String message) {
        return new SecondFactorMismatchException(message, true);
    }

    public boolean isSetupRequired() {
        return setupRequired;
    }

}
