package com.platform.learning.exception;

/**
 * Automated scoring isn't implemented for this question type yet. Thrown rather
 * than guessing (e.g. silently marking a MATCHING answer incorrect) - a wrong
 * "incorrect" verdict on a genuinely correct answer would be actively harmful to a
 * child using the app, worse than clearly refusing to score it at all. Maps to
 * HTTP 501.
 *
 * <p>Battle 5A: also thrown when published content cannot be scored as authored
 * (a TRUE_FALSE or NUMERIC version whose correct_answer is not in the canonical
 * format) - such content predates publish-time validation. Either way nothing is
 * persisted, so no learning evidence is recorded.
 */
public final class UnscorableAnswerException extends RuntimeException {

    public UnscorableAnswerException(String message) {
        super(message);
    }

}