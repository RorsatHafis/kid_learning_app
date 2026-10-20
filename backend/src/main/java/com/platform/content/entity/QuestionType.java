package com.platform.content.entity;

/**
 * The kinds of question the player can show and the server can score.
 */
public enum QuestionType {
    
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER,
    NUMERIC,
    MATCHING,
    /** Put the items in order (V46): the version's options, in their display order. */
    ORDERING,
    SORTING

}
