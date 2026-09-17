package com.platform.learning.service;

import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.learning.exception.UnscorableAnswerException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Scores a submitted answer against a QuestionVersion's known-correct answer.
 * Never trusts a client-supplied correctness flag (Section 25: "scoring must be
 * server-controlled") - this is the one and only place that decision gets made.
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
            case TRUE_FALSE, SHORT_ANSWER -> scoreByExactText(version.getCorrectAnswer(), submittedAnswer);
            case NUMERIC -> scoreNumeric(version.getCorrectAnswer(), submittedAnswer);
            // MATCHING needs a pairing structure this schema doesn't model yet (QuestionOption
            // has no "pairs with" concept) - refusing to guess rather than risk marking a
            // genuinely correct answer wrong (see UnscorableAnswerException's javadoc).
            case MATCHING -> throw new UnscorableAnswerException(
                    "Automated scoring for MATCHING questions is not implemented yet");
        };
    }

    /** submittedAnswer is expected to be the chosen QuestionOption's id, as a string. */
    private boolean scoreByOption(java.util.UUID questionVersionId, String submittedAnswer) {
        return questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(questionVersionId).stream()
                .filter(option -> option.getId().toString().equals(submittedAnswer))
                .findFirst()
                .map(QuestionOption::isCorrect)
                .orElse(false);
    }

    private boolean scoreByExactText(String correctAnswer, String submittedAnswer) {
        return correctAnswer != null && submittedAnswer != null
                && correctAnswer.trim().equalsIgnoreCase(submittedAnswer.trim());
    }

    private boolean scoreNumeric(String correctAnswer, String submittedAnswer) {
        if (correctAnswer == null || submittedAnswer == null) {
            return false;
        }
        try {
            return new BigDecimal(correctAnswer.trim()).compareTo(new BigDecimal(submittedAnswer.trim())) == 0;
        } catch (NumberFormatException e) {
            // A malformed submission is simply an incorrect answer, not a server error.
            return false;
        }
    }

}