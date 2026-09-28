package com.platform.family;

import com.platform.content.entity.MatchSide;
import com.platform.content.entity.QuestionOption;
import com.platform.family.service.FamilyDataExportService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The export shows answers as the child saw them: the words of a choice, and matched pairs. */
class FamilyDataExportReadableAnswerTests {

    private static final UUID VERSION = UUID.randomUUID();

    @Test
    void choiceIdsBecomeTheirWordsAndMatchingBecomesPairs() throws Exception {
        QuestionOption cat = option(QuestionOption.create(VERSION, "Cat", true, 0));
        QuestionOption dog = option(QuestionOption.create(VERSION, "Dog", false, 1));
        assertThat(readable(dog.getId().toString(), List.of(cat, dog))).isEqualTo("Dog");

        QuestionOption one = option(QuestionOption.matchItem(VERSION, "1", MatchSide.LEFT, 1, 0));
        QuestionOption dot = option(QuestionOption.matchItem(VERSION, "•", MatchSide.RIGHT, 1, 1));
        String pairs = "{\"" + one.getId() + "\":\"" + dot.getId() + "\"}";
        assertThat(readable(pairs, List.of(one, dot))).isEqualTo("1 → •");
    }

    @Test
    void freeAnswersAndUnreadableValuesStayAsGiven() throws Exception {
        assertThat(readable("7", List.of())).isEqualTo("7");
        QuestionOption cat = option(QuestionOption.create(VERSION, "Cat", true, 0));
        assertThat(readable("{not json", List.of(cat))).isEqualTo("{not json");
        assertThat(readable("something else", List.of(cat))).isEqualTo("something else");
    }

    private static QuestionOption option(QuestionOption option) {
        ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
        return option;
    }

    private static String readable(String submitted, List<QuestionOption> choices) throws Exception {
        Method method = FamilyDataExportService.class.getDeclaredMethod("readable", String.class, List.class);
        method.setAccessible(true);
        return (String) method.invoke(null, submitted, choices);
    }

}
