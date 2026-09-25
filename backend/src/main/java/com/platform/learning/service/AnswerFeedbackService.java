package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.content.service.CanonicalAnswers;
import com.platform.learning.entity.AnswerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Feedback on an answer the child has already given: the correct answer, so a
 * mistake can be explained rather than only marked (product directive §12.4:
 * incorrect feedback is "supportive, non-shaming, explanatory, recoverable").
 *
 * <p>Only ever built from a persisted {@link AnswerRecord} - i.e. after the child
 * answered that question in that attempt, which they cannot answer again (one answer
 * per attempt and question, V30). The learner content endpoint still never exposes an
 * answer key before answering ({@code ActivityContentDtos}).
 */
@Service
public class AnswerFeedbackService {

    private final QuestionVersionRepository questionVersionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;

    public AnswerFeedbackService(QuestionVersionRepository questionVersionRepository,
                                 QuestionRepository questionRepository,
                                 QuestionOptionRepository questionOptionRepository) {
        this.questionVersionRepository = questionVersionRepository;
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
    }

    /**
     * @param optionIds for MULTIPLE_CHOICE, the correct option(s); empty otherwise
     * @param text      the correct answer as the child would read it (option label,
     *                  "true"/"false", the number, or the expected text)
     */
    public record CorrectAnswer(List<UUID> optionIds, String text) {
    }

    @Transactional(readOnly = true)
    public CorrectAnswer correctAnswerFor(AnswerRecord answer) {
        QuestionVersion version = questionVersionRepository.findById(answer.getQuestionVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", answer.getQuestionVersionId()));
        Question question = questionRepository.findById(version.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question", version.getQuestionId()));

        return switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE -> {
                List<QuestionOption> correct = questionOptionRepository
                        .findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId()).stream()
                        .filter(QuestionOption::isCorrect)
                        .toList();
                yield new CorrectAnswer(correct.stream().map(QuestionOption::getId).toList(),
                        String.join(", ", correct.stream().map(QuestionOption::getLabel).toList()));
            }
            case TRUE_FALSE -> new CorrectAnswer(List.of(), CanonicalAnswers.parseTrueFalse(version.getCorrectAnswer())
                    .map(String::valueOf).orElse(null));
            case NUMERIC -> new CorrectAnswer(List.of(), CanonicalAnswers.parseNumber(version.getCorrectAnswer())
                    .map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString).orElse(null));
            case SHORT_ANSWER -> new CorrectAnswer(List.of(),
                    version.getCorrectAnswer() == null ? null : version.getCorrectAnswer().trim());
            // Never published (QuestionService refuses it), so never answered.
            case MATCHING -> new CorrectAnswer(List.of(), null);
        };
    }

}
