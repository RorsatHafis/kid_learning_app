package com.platform.subscription.subjects;

/** V89: the family's plan doesn't include this subject (409, {@code subjectNotInPlan}); nothing already there is lost. */
public class SubjectNotInPlanException extends RuntimeException {

    public SubjectNotInPlanException() {
        super("This subject isn't part of your family's plan.");
    }
}
