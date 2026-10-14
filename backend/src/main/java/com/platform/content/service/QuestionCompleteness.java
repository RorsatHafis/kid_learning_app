package com.platform.content.service;

import com.platform.content.entity.MatchSide;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.validation.ValidationReport.Issue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether a question version is complete enough to score: the answer contract
 * {@code AnswerScoringService} relies on. Used to refuse publishing
 * ({@link QuestionService#publish}) and to explain drafts in the Content Studio.
 */
public final class QuestionCompleteness {

    private QuestionCompleteness() {
    }

    public static final int MIN_PAIRS = 2;
    public static final int MAX_PAIRS = 6;

    /** Why the version can't be scored, or empty when it can. */
    public static Optional<Issue> problem(QuestionType questionType, QuestionVersion version, List<QuestionOption> options) {
        if (version.getPromptImageAssetId() != null && version.getPromptImageDescription() == null) {
            return Optional.of(new Issue("PROMPT_IMAGE_UNDESCRIBED",
                    "Describe the question's picture: children who use a screen reader hear the description instead."));
        }
        return Optional.ofNullable(switch (questionType) {
            case MULTIPLE_CHOICE -> {
                long correctCount = options.stream().filter(QuestionOption::isCorrect).count();
                if (options.size() < 2) {
                    yield new Issue("CHOICES_TOO_FEW", "A multiple-choice question needs at least 2 answer choices (it has %d)."
                            .formatted(options.size()), Map.of("min", 2, "count", options.size()));
                }
                yield correctCount != 1
                        ? new Issue("CHOICES_CORRECT_COUNT", "Exactly one answer choice must be marked correct (%d are)."
                                .formatted(correctCount), Map.of("count", correctCount))
                        : null;
            }
            case MATCHING -> matchingProblem(options);
            case ORDERING -> orderingProblem(options);
            case SORTING -> sortingProblem(options);
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

    /**
     * A MATCHING version scores only if every option is a matching item, pairs 0..n-1 each
     * have exactly one left and one right, and no two items on the same side read the same:
     * with two identical right items a child could choose the "wrong" one of two equal
     * answers and be marked wrong for it.
     */
    private static Issue matchingProblem(List<QuestionOption> options) {
        if (options.stream().anyMatch(o -> o.getMatchSide() == null)) {
            return new Issue("MATCHING_NOT_PAIRED", "Every item of a matching question must belong to a pair.");
        }
        List<QuestionOption> lefts = options.stream().filter(o -> o.getMatchSide() == MatchSide.LEFT).toList();
        List<QuestionOption> rights = options.stream().filter(o -> o.getMatchSide() == MatchSide.RIGHT).toList();
        if (lefts.size() < MIN_PAIRS || lefts.size() > MAX_PAIRS) {
            return new Issue("MATCHING_PAIR_COUNT", "A matching question needs %d to %d pairs (it has %d)."
                    .formatted(MIN_PAIRS, MAX_PAIRS, lefts.size()), Map.of("min", MIN_PAIRS, "max", MAX_PAIRS, "count", lefts.size()));
        }
        Set<Integer> leftPairs = lefts.stream().map(QuestionOption::getMatchPair).collect(Collectors.toSet());
        Set<Integer> rightPairs = rights.stream().map(QuestionOption::getMatchPair).collect(Collectors.toSet());
        if (rights.size() != lefts.size() || !leftPairs.equals(rightPairs)) {
            return new Issue("MATCHING_PAIR_INCOMPLETE", "Every pair needs one item on each side.");
        }
        if (hasDuplicateLabels(lefts) || hasDuplicateLabels(rights)) {
            return new Issue("MATCHING_DUPLICATE_ITEM",
                    "Two items on the same side read the same, so a child couldn't tell which one is meant.");
        }
        return null;
    }

    /** ORDERING: 3-8 items, and at least two that differ - otherwise every order is the same. */
    private static Issue orderingProblem(List<QuestionOption> items) {
        if (items.size() < OrderingItems.MIN_ITEMS || items.size() > OrderingItems.MAX_ITEMS) {
            return new Issue("ORDERING_ITEM_COUNT", "An ordering question needs %d to %d items (it has %d)."
                    .formatted(OrderingItems.MIN_ITEMS, OrderingItems.MAX_ITEMS, items.size()),
                    Map.of("min", OrderingItems.MIN_ITEMS, "max", OrderingItems.MAX_ITEMS, "count", items.size()));
        }
        if (items.stream().anyMatch(o -> o.getMatchSide() != null)) {
            return new Issue("ORDERING_NOT_ITEMS", "Every item of an ordering question must be one of the things to put in order.");
        }
        if (OrderingItems.keys(items).stream().distinct().count() < 2) {
            return new Issue("ORDERING_ALL_SAME", "The items are all the same, so every order would be right.");
        }
        return null;
    }

    public static final int MIN_GROUPS = 2;
    public static final int MAX_GROUPS = 4;
    public static final int MIN_SORT_ITEMS = 4;
    public static final int MAX_SORT_ITEMS = 10;

    /**
     * SORTING (X23): 2-4 groups numbered 0..n-1 with different names, 4-10 items each in one of
     * them, and every group used - a group nothing belongs to would only be a trap. Two items that
     * read the same must be in the same group, or a child could be marked wrong for a right answer.
     */
    private static Issue sortingProblem(List<QuestionOption> options) {
        if (options.stream().anyMatch(o -> o.getSortRole() == null)) {
            return new Issue("SORTING_NOT_GROUPED", "Every option of a sorting question must be a group or an item.");
        }
        List<QuestionOption> groups = options.stream().filter(o -> o.getSortRole() == com.platform.content.entity.SortRole.GROUP).toList();
        List<QuestionOption> items = options.stream().filter(o -> o.getSortRole() == com.platform.content.entity.SortRole.ITEM).toList();
        if (groups.size() < MIN_GROUPS || groups.size() > MAX_GROUPS) {
            return new Issue("SORTING_GROUP_COUNT", "A sorting question needs %d to %d groups (it has %d)."
                    .formatted(MIN_GROUPS, MAX_GROUPS, groups.size()), Map.of("min", MIN_GROUPS, "max", MAX_GROUPS, "count", groups.size()));
        }
        if (items.size() < MIN_SORT_ITEMS || items.size() > MAX_SORT_ITEMS) {
            return new Issue("SORTING_ITEM_COUNT", "A sorting question needs %d to %d items (it has %d)."
                    .formatted(MIN_SORT_ITEMS, MAX_SORT_ITEMS, items.size()),
                    Map.of("min", MIN_SORT_ITEMS, "max", MAX_SORT_ITEMS, "count", items.size()));
        }
        Set<Integer> numbers = groups.stream().map(QuestionOption::getSortGroup).collect(Collectors.toSet());
        if (items.stream().anyMatch(item -> !numbers.contains(item.getSortGroup()))) {
            return new Issue("SORTING_ITEM_WITHOUT_GROUP", "Every item must belong to one of the groups.");
        }
        if (!items.stream().map(QuestionOption::getSortGroup).collect(Collectors.toSet()).equals(numbers)) {
            return new Issue("SORTING_EMPTY_GROUP", "Every group needs at least one item that belongs in it.");
        }
        if (hasDuplicateLabels(groups)) {
            return new Issue("SORTING_DUPLICATE_GROUP", "Two groups have the same name, so a child couldn't tell them apart.");
        }
        java.util.Map<String, Integer> groupOfLabel = new java.util.HashMap<>();
        for (QuestionOption item : items) {
            String key = item.getLabel().strip().toLowerCase(Locale.ROOT) + "|" + item.getImageAssetId() + "|" + item.getAudioAssetId();
            Integer earlier = groupOfLabel.putIfAbsent(key, item.getSortGroup());
            if (earlier != null && !earlier.equals(item.getSortGroup())) {
                return new Issue("SORTING_SAME_ITEM_TWO_GROUPS", "Two items look the same but belong to different groups.");
            }
        }
        return null;
    }

    private static boolean hasDuplicateLabels(List<QuestionOption> items) {
        return items.stream().map(o -> o.getLabel().strip().toLowerCase(Locale.ROOT)).distinct().count() != items.size();
    }

}
