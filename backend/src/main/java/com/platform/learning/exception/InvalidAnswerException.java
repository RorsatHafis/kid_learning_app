package com.platform.learning.exception;

/**
 * The submitted answer is not a valid response to this question at all - e.g. a
 * MULTIPLE_CHOICE answer that does not reference one of this question version's
 * own options, a TRUE_FALSE answer that is not {@code true}/{@code false}, or a
 * NUMERIC answer that is not a number. Distinct from an incorrect answer: an
 * incorrect answer is learning evidence, an invalid one is a broken request and
 * must never be scored or recorded as evidence. Thrown before anything is
 * persisted; maps to HTTP 422.
 *
 * <p>The message is deliberately generic and never names the valid options or
 * the correct answer.
 */
public final class InvalidAnswerException extends RuntimeException {

    public InvalidAnswerException(String message) {
        super(message);
    }

}
