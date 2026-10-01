package com.platform.learning.service;

import com.platform.content.entity.MatchSide;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.content.service.CanonicalAnswers;
import com.platform.content.service.OrderingItems;
import com.platform.learning.exception.InvalidAnswerException;
import com.platform.learning.exception.UnscorableAnswerException;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li>MATCHING: a JSON object mapping every left item's option id to the id of
 *       the right item the child matched it with, each right item used once. Correct
 *       only when every pair is right: evidence is one answer, right or wrong, as for
 *       every other type (partial credit would change the evidence model).</li>
 *   <li>ORDERING: a JSON array of every item's option id, each once, in the order the child
 *       put them. Correct when the items read in the correct order ({@link OrderingItems}):
 *       interchangeable items (the same letter twice) may go either way round.</li>
 * </ul>
 * A submission that is not a valid response under this contract throws
 * {@link InvalidAnswerException} before anything is persisted, so it can never
 * become (negative) learning evidence. Content that cannot be scored as authored
 * throws {@link UnscorableAnswerException}.
 */
@Service
public class AnswerScoringService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
            case MATCHING -> scoreMatching(version.getId(), submittedAnswer);
            case ORDERING -> scoreOrdering(version.getId(), submittedAnswer);
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

    private boolean scoreMatching(UUID questionVersionId, String submittedAnswer) {
        Map<UUID, QuestionOption> items = new HashMap<>();
        questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(questionVersionId)
                .forEach(option -> items.put(option.getId(), option));
        long leftCount = items.values().stream().filter(o -> o.getMatchSide() == MatchSide.LEFT).count();
        if (leftCount == 0) {
            throw new UnscorableAnswerException("This matching question has no pairs and cannot be scored");
        }
        JsonNode answer;
        try {
            answer = JSON.readTree(submittedAnswer == null ? "" : submittedAnswer);
        } catch (JacksonException e) {
            throw new InvalidAnswerException("A matching answer must pair each item with one match");
        }
        if (answer == null || !answer.isObject() || answer.size() != leftCount) {
            throw new InvalidAnswerException("A matching answer must pair every item with one match");
        }
        Set<UUID> usedRights = new HashSet<>();
        boolean allCorrect = true;
        for (Map.Entry<String, JsonNode> pair : answer.properties()) {
            QuestionOption left = items.get(parseOptionId(pair.getKey()));
            QuestionOption right = pair.getValue().isString() ? items.get(parseOptionId(pair.getValue().asString())) : null;
            if (left == null || left.getMatchSide() != MatchSide.LEFT || right == null || right.getMatchSide() != MatchSide.RIGHT
                    || !usedRights.add(right.getId())) {
                throw new InvalidAnswerException("A matching answer must pair each item with a different match");
            }
            allCorrect &= left.getMatchPair().equals(right.getMatchPair());
        }
        return allCorrect;
    }

    private boolean scoreOrdering(UUID questionVersionId, String submittedAnswer) {
        List<QuestionOption> items = questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(questionVersionId);
        if (items.size() < 2) {
            throw new UnscorableAnswerException("This ordering question has no items to put in order and cannot be scored");
        }
        Map<UUID, QuestionOption> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        JsonNode answer;
        try {
            answer = JSON.readTree(submittedAnswer == null ? "" : submittedAnswer);
        } catch (JacksonException e) {
            throw new InvalidAnswerException("An ordering answer must place every item once");
        }
        if (answer == null || !answer.isArray() || answer.size() != items.size()) {
            throw new InvalidAnswerException("An ordering answer must place every item once");
        }
        Set<UUID> placed = new HashSet<>();
        List<QuestionOption> order = new java.util.ArrayList<>();
        for (JsonNode node : answer) {
            QuestionOption item = node.isString() ? byId.get(parseOptionId(node.asString())) : null;
            if (item == null || !placed.add(item.getId())) {
                throw new InvalidAnswerException("An ordering answer must place every item once");
            }
            order.add(item);
        }
        return OrderingItems.keys(order).equals(OrderingItems.keys(items));
    }

    private static UUID parseOptionId(String submittedAnswer) {
        try {
            return UUID.fromString(submittedAnswer == null ? "" : submittedAnswer.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswerException("The submitted answer is not one of this question's options");
        }
    }

}
