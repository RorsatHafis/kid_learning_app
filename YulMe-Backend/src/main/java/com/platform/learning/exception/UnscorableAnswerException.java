package com.platform.learning.exception;

/**
 * Automated scoring isn't implemented for this question type yet. Thrown rather
 * than guessing (e.g. silently marking a MATCHING answer incorrect) - a wrong
 * "incorrect" verdict on a genuinely correct answer would be actively harmful to a
 * child using the app, worse than clearly refusing to score it at all. Maps to
 * HTTP 501.
 */
public final class UnscorableAnswerException extends RuntimeException {

    public UnscorableAnswerException(String message) {
        super(message);
    }

}