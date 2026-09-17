package com.platform.content.exception;

/**
 * A question version doesn't have what its QuestionType needs to be usable (e.g. a
 * MULTIPLE_CHOICE question with no options, or with more than one marked correct).
 * Maps to HTTP 422 - this is exactly the "prevent incomplete content from becoming
 * learner-visible" validation the master prompt calls for, enforced at publish time.
 */
public final class IncompleteQuestionException extends RuntimeException {

    public IncompleteQuestionException(String message) {
        super(message);
    }

}