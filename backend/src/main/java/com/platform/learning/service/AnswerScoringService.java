package com.platform.learning.service;

import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.content.service.CanonicalAnswers;
import com.platform.learning.exception.InvalidAnswerException;
import com.platform.learning.exception.UnscorableAnswerException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Scores a submitted answer against a QuestionVersion's known-correct answer.
 * Never trusts a client-supplied correctness flag (Section 25: "scoring must be
 * server-controlled") - this is the one and only place that decision gets made.
 *
 * <p>Battle 5A answer contract - {@code submittedAnswer} per question type:
 * <ul>
 *   <li>MULTIPLE_CHOICE: the chosen QuestionOption's id (UUID string). The id is
 *       the option's stable identity; its label is display text only (not unique,
 *       mutable via {@code relabel}, a future localization target).</li>
 *   <li>TRUE_FALSE: {@code "true"} or {@code "false"} (case-insensitive), compared
 *       with the version's correct_answer read the same way.</li>
 *   <li>SHORT_ANSWER: free text, trimmed, case-insensitive exact match.</li>
 *   <li>NUMERIC: a decimal number, compared by numeric value.</li>
 *   <li>MATCHING: not scorable yet (publication is refused - see QuestionService).</li>
 * </ul>
 * A submission that is not a valid response under this contract throws
 * {@link InvalidAnswerException} before anything is persisted, so it can never
 * become (negative) learning evidence. Content that cannot be scored as authored
 * throws {@link UnscorableAnswerException}.
 */
@Service
public class AnswerScoringService {

    private final QuestionOptionRepository questionOptionRepository;

    public AnswerScoringService(QuestionOptionRepository questionOptionRepository) {
        this.questionOptionRepository = questionOptionRepository;
    }

    public boolean score(QuestionType questionType, QuestionVersion version, String submittedAnswer) {
        return switch (questionType) {
            case MULTIPLE_CHOICE -> scoreByOption(version.getId(), submittedAnswer);
            case TRUE_FALSE -> scoreTrueFalse(version.getCorrectAnswer(), submittedAnswer);
            case SHORT_ANSWER -> scoreByExactText(version.getCorrectAnswer(), submittedAnswer);
            case NUMERIC -> scoreNumeric(version.getCorrectAnswer(), submittedAnswer);
            // MATCHING needs a pairing structure this schema doesn't model yet (QuestionOption
            // has no "pairs with" concept) - refusing to guess rather than risk marking a
            // genuinely correct answer wrong (see UnscorableAnswerException's javadoc).
            case MATCHING -> throw new UnscorableAnswerException(
                    "Automated scoring for MATCHING questions is not implemented yet");
        };
    }

    /** submittedAnswer must be the id of one of this question version's own options. */
    private boolean scoreByOption(UUID questionVersionId, String submittedAnswer) {
        UUID chosenOptionId = parseOptionId(submittedAnswer);
        return questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(questionVersionId).stream()
                .filter(option -> option.getId().equals(chosenOptionId))
                .findFirst()
                .map(QuestionOption::isCorrect)
                .orElseThrow(() -> new InvalidAnswerException(
                        "The submitted answer is not one of this question's options"));
    }

    private boolean scoreTrueFalse(String correctAnswer, String submittedAnswer) {
        Boolean submitted = CanonicalAnswers.parseTrueFalse(submittedAnswer)
                .orElseThrow(() -> new InvalidAnswerException(
                        "A true/false answer must be \"true\" or \"false\""));
        Boolean correct = CanonicalAnswers.parseTrueFalse(correctAnswer)
                .orElseThrow(() -> new UnscorableAnswerException(
                        "This true/false question has no valid correct answer and cannot be scored"));
        return submitted.equals(correct);
    }

    private boolean scoreByExactText(String correctAnswer, String submittedAnswer) {
        return correctAnswer != null && submittedAnswer != null
                && correctAnswer.trim().equalsIgnoreCase(submittedAnswer.trim());
    }

    private boolean scoreNumeric(String correctAnswer, String submittedAnswer) {
        BigDecimal submitted = CanonicalAnswers.parseNumber(submittedAnswer)
                .orElseThrow(() -> new InvalidAnswerException("A numeric answer must be a number"));
        BigDecimal correct = CanonicalAnswers.parseNumber(correctAnswer)
                .orElseThrow(() -> new UnscorableAnswerException(
                        "This numeric question has no valid correct answer and cannot be scored"));
        return correct.compareTo(submitted) == 0;
    }

    private static UUID parseOptionId(String submittedAnswer) {
        try {
            return UUID.fromString(submittedAnswer == null ? "" : submittedAnswer.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswerException("The submitted answer is not one of this question's options");
        }
    }

}
