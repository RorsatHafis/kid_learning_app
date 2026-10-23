package com.platform.security.stepup;

/** M8: the action needs the password re-entered first (answered 403 with {@code stepUpRequired}). */
public class StepUpRequiredException extends RuntimeException {

    public StepUpRequiredException() {
        super("Enter your password again to do this");
    }

}
