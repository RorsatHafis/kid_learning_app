package com.platform.content.service;

import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.validation.ValidationReport.Issue;

import java.util.List;
import java.util.Optional;

/**
 * Whether a question version is complete enough to score: the answer contract
 * {@code AnswerScoringService} relies on. Used to refuse publishing
 * ({@link QuestionService#publish}) and to explain drafts in the Content Studio.
 */
public final class QuestionCompleteness {

    private QuestionCompleteness() {
    }

    /** Why the version can't be scored, or empty when it can. */
    public static Optional<Issue> problem(QuestionType questionType, QuestionVersion version, List<QuestionOption> options) {
        return Optional.ofNullable(switch (questionType) {
            case MULTIPLE_CHOICE -> {
                long correctCount = options.stream().filter(QuestionOption::isCorrect).count();
                if (options.size() < 2) {
                    yield new Issue("CHOICES_TOO_FEW", "A multiple-choice question needs at least 2 answer choices (it has %d)."
                            .formatted(options.size()));
                }
                yield correctCount != 1
                        ? new Issue("CHOICES_CORRECT_COUNT", "Exactly one answer choice must be marked correct (%d are)."
                                .formatted(correctCount))
                        : null;
            }
            // Battle 5A: MATCHING has no scoring implementation (AnswerScoringService throws
            // UnscorableAnswerException), so a published MATCHING question could never be
            // answered and would leave any activity containing it uncompletable. Refused
            // until pairing is modelled and scored, rather than published broken.
            case MATCHING -> new Issue("MATCHING_UNSUPPORTED",
                    "Matching questions can't be scored yet, so they can't be published.");
            // TRUE_FALSE and NUMERIC must use the same canonical formats AnswerScoringService
            // scores against (CanonicalAnswers), so published content is always scorable.
            case TRUE_FALSE -> CanonicalAnswers.parseTrueFalse(version.getCorrectAnswer()).isEmpty()
                    ? new Issue("ANSWER_NOT_TRUE_FALSE", "The correct answer must be true or false.")
                    : null;
            case NUMERIC -> CanonicalAnswers.parseNumber(version.getCorrectAnswer()).isEmpty()
                    ? new Issue("ANSWER_NOT_NUMERIC", "The correct answer must be a number.")
                    : null;
            case SHORT_ANSWER -> version.getCorrectAnswer() == null || version.getCorrectAnswer().isBlank()
                    ? new Issue("ANSWER_MISSING", "The question needs a correct answer.")
                    : null;
        });
    }

}
