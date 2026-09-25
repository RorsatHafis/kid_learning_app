package com.platform.content.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * The canonical value formats for correct_answer-based question types, shared by
 * publication validation ({@link QuestionService}) and scoring
 * ({@code AnswerScoringService}) so authoring and scoring can never disagree
 * about what a valid TRUE_FALSE or NUMERIC answer looks like.
 */
public final class CanonicalAnswers {

    private CanonicalAnswers() {
    }

    /** {@code "true"} / {@code "false"}, trimmed and case-insensitive; anything else is empty. */
    public static Optional<Boolean> parseTrueFalse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> Optional.of(Boolean.TRUE);
            case "false" -> Optional.of(Boolean.FALSE);
            default -> Optional.empty();
        };
    }

    /** A decimal number, trimmed; anything unparseable is empty. */
    public static Optional<BigDecimal> parseNumber(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

}
