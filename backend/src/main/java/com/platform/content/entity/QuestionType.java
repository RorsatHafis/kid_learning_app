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
    SORTING,
    /**
     * Owner-approved 2026-11-10: the child draws or writes on the screen. Recorded, never scored - no automatic grading,
     * mastery or Smart Review; parents and the class's teachers review it (V91).
     */
    DRAWING

}
