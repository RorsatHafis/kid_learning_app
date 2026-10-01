package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Question;
import com.platform.content.entity.MatchSide;
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
import java.util.ArrayList;
import java.util.Comparator;
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
     * @param optionIds for MULTIPLE_CHOICE, the correct option(s); for ORDERING, every item in the correct order; empty otherwise
     * @param text      the correct answer as the child would read it (option label,
     *                  "true"/"false", the number, the expected text, or the pairs)
     * @param matches   for MATCHING, each left item's id with its match's id; empty otherwise
     */
    public record CorrectAnswer(List<UUID> optionIds, String text, List<MatchPair> matches) {
        CorrectAnswer(List<UUID> optionIds, String text) {
            this(optionIds, text, List.of());
        }
    }

    public record MatchPair(UUID leftOptionId, UUID rightOptionId) {
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
            case MATCHING -> {
                List<QuestionOption> items = questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId());
                List<QuestionOption> lefts = items.stream().filter(o -> o.getMatchSide() == MatchSide.LEFT)
                        .sorted(Comparator.comparing(QuestionOption::getMatchPair)).toList();
                List<MatchPair> matches = new ArrayList<>();
                List<String> words = new ArrayList<>();
                for (QuestionOption left : lefts) {
                    items.stream().filter(o -> o.getMatchSide() == MatchSide.RIGHT && o.getMatchPair().equals(left.getMatchPair()))
                            .findFirst().ifPresent(right -> {
                                matches.add(new MatchPair(left.getId(), right.getId()));
                                words.add(left.getLabel() + " → " + right.getLabel());
                            });
                }
                yield new CorrectAnswer(List.of(), String.join(", ", words), matches);
            }
            // The items in their correct order: optionIds is that order.
            case ORDERING -> {
                List<QuestionOption> items = questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId());
                yield new CorrectAnswer(items.stream().map(QuestionOption::getId).toList(),
                        String.join(" → ", items.stream().map(QuestionOption::getLabel).toList()));
            }
        };
    }

}
