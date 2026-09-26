package com.platform.learning.service;

import com.platform.content.entity.MatchSide;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.learning.exception.InvalidAnswerException;
import com.platform.learning.exception.UnscorableAnswerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Battle 5A answer contract: an answer is either scored (correct / incorrect -
 * both legitimate evidence) or rejected before scoring. A value that is not a
 * valid response to the question must never be scored as "incorrect", because
 * that would silently become negative mastery evidence.
 */
@ExtendWith(MockitoExtension.class)
class AnswerScoringServiceTests {

    @Mock private QuestionOptionRepository questionOptionRepository;

    private AnswerScoringService scoring;

    @BeforeEach
    void setUp() {
        scoring = new AnswerScoringService(questionOptionRepository);
    }

    @Test
    void multipleChoiceIsScoredByTheChosenOptionsId() {
        QuestionVersion version = version("2 + 2 = ?", null);
        QuestionOption three = option(version, "Three", false);
        QuestionOption four = option(version, "Four", true);
        when(questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId()))
                .thenReturn(List.of(three, four));

        assertThat(scoring.score(QuestionType.MULTIPLE_CHOICE, version, four.getId().toString())).isTrue();
        assertThat(scoring.score(QuestionType.MULTIPLE_CHOICE, version, three.getId().toString())).isFalse();
    }

    @Test
    void multipleChoiceRejectsALabelOrAnOptionThatIsNotThisQuestions() {
        QuestionVersion version = version("2 + 2 = ?", null);
        QuestionOption four = option(version, "Four", true);
        when(questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId()))
                .thenReturn(List.of(four));

        // The label - what the old frontend sent - is display text, not an answer identity.
        assertThatThrownBy(() -> scoring.score(QuestionType.MULTIPLE_CHOICE, version, "Four"))
                .isInstanceOf(InvalidAnswerException.class);
        assertThatThrownBy(() -> scoring.score(QuestionType.MULTIPLE_CHOICE, version, UUID.randomUUID().toString()))
                .isInstanceOf(InvalidAnswerException.class);
    }

    @Test
    void trueFalseUsesCanonicalValuesAndRejectsAnythingElse() {
        QuestionVersion version = version("Is 3 odd?", "TRUE");

        assertThat(scoring.score(QuestionType.TRUE_FALSE, version, "true")).isTrue();
        assertThat(scoring.score(QuestionType.TRUE_FALSE, version, " False ")).isFalse();
        assertThatThrownBy(() -> scoring.score(QuestionType.TRUE_FALSE, version, "maybe"))
                .isInstanceOf(InvalidAnswerException.class);
    }

    @Test
    void trueFalseWithANonCanonicalCorrectAnswerIsUnscorableNotSilentlyWrong() {
        QuestionVersion legacy = version("Is 2 even?", "yes");

        assertThatThrownBy(() -> scoring.score(QuestionType.TRUE_FALSE, legacy, "true"))
                .isInstanceOf(UnscorableAnswerException.class);
    }

    @Test
    void numericComparesByValueAndRejectsNonNumbers() {
        QuestionVersion version = version("1 + 1 = ?", "2");

        assertThat(scoring.score(QuestionType.NUMERIC, version, "2.0")).isTrue();
        assertThat(scoring.score(QuestionType.NUMERIC, version, "3")).isFalse();
        assertThatThrownBy(() -> scoring.score(QuestionType.NUMERIC, version, "abc"))
                .isInstanceOf(InvalidAnswerException.class);
        assertThatThrownBy(() -> scoring.score(QuestionType.NUMERIC, version("1 + 1 = ?", "two"), "2"))
                .isInstanceOf(UnscorableAnswerException.class);
    }

    @Test
    void shortAnswerIsTrimmedCaseInsensitiveExactMatch() {
        QuestionVersion version = version("Say hello", "Hello");

        assertThat(scoring.score(QuestionType.SHORT_ANSWER, version, "  hello ")).isTrue();
        assertThat(scoring.score(QuestionType.SHORT_ANSWER, version, "hi")).isFalse();
    }

    // L9: MATCHING was refused until pairing was modelled (V40); it is now scored, and
    // anything that is not a complete one-to-one pairing is refused, never scored wrong.

    @Test
    void matchingIsCorrectOnlyWhenEveryPairIsRight() {
        QuestionVersion version = version("Match the animal to its sound", null);
        QuestionOption dog = match(version, "Dog", MatchSide.LEFT, 0);
        QuestionOption woof = match(version, "Woof", MatchSide.RIGHT, 0);
        QuestionOption cat = match(version, "Cat", MatchSide.LEFT, 1);
        QuestionOption meow = match(version, "Meow", MatchSide.RIGHT, 1);
        when(questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId()))
                .thenReturn(List.of(dog, woof, cat, meow));

        assertThat(scoring.score(QuestionType.MATCHING, version, pairs(dog, woof, cat, meow))).isTrue();
        assertThat(scoring.score(QuestionType.MATCHING, version, pairs(dog, meow, cat, woof))).isFalse();
    }

    @Test
    void anIncompleteOrMalformedPairingIsRefusedNotScoredWrong() {
        QuestionVersion version = version("Match", null);
        QuestionOption dog = match(version, "Dog", MatchSide.LEFT, 0);
        QuestionOption woof = match(version, "Woof", MatchSide.RIGHT, 0);
        QuestionOption cat = match(version, "Cat", MatchSide.LEFT, 1);
        QuestionOption meow = match(version, "Meow", MatchSide.RIGHT, 1);
        when(questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId()))
                .thenReturn(List.of(dog, woof, cat, meow));

        for (String answer : new String[] {
                "not json",
                "[]",
                "{\"" + dog.getId() + "\":\"" + woof.getId() + "\"}",                        // a pair missing
                pairs(dog, woof, cat, woof),                                                    // one match used twice
                pairs(dog, woof, woof, meow),                                                   // a right item on the left
                "{\"" + dog.getId() + "\":\"" + woof.getId() + "\",\"" + cat.getId() + "\":7}",  // not an id
                pairs(dog, woof, cat, match(version, "Moo", MatchSide.RIGHT, 1))}) {             // not this question's
            assertThatThrownBy(() -> scoring.score(QuestionType.MATCHING, version, answer)).as(answer)
                    .isInstanceOf(InvalidAnswerException.class);
        }
    }

    @Test
    void aMatchingQuestionWithoutPairsIsUnscorable() {
        QuestionVersion version = version("Match", null);
        when(questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> scoring.score(QuestionType.MATCHING, version, "{}"))
                .isInstanceOf(UnscorableAnswerException.class);
    }

    private static String pairs(QuestionOption left1, QuestionOption right1, QuestionOption left2, QuestionOption right2) {
        return "{\"" + left1.getId() + "\":\"" + right1.getId() + "\",\"" + left2.getId() + "\":\"" + right2.getId() + "\"}";
    }

    private static QuestionOption match(QuestionVersion version, String label, MatchSide side, int pair) {
        QuestionOption option = QuestionOption.matchItem(version.getId(), label, side, pair, pair);
        ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
        return option;
    }

    private static QuestionVersion version(String prompt, String correctAnswer) {
        QuestionVersion version = QuestionVersion.draft(UUID.randomUUID(), 1, prompt, correctAnswer, null);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        return version;
    }

    private static QuestionOption option(QuestionVersion version, String label, boolean correct) {
        QuestionOption option = QuestionOption.create(version.getId(), label, correct, 0);
        ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
        return option;
    }

}
