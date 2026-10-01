package com.platform.content.service;

import com.platform.content.entity.QuestionOption;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ORDERING items are never handed out already in order, and the start is the same every time (X11). */
class OrderingItemsTests {

    @Test
    void theLearnersStartIsNeverTheAnswerAndIsStable() {
        for (int run = 0; run < 2000; run++) {
            UUID versionId = UUID.randomUUID();
            List<String> labels = run % 2 == 0 ? List.of("1", "2", "3") : List.of("b", "o", "o", "k");
            List<QuestionOption> items = items(versionId, labels);

            List<QuestionOption> given = OrderingItems.learnerOrder(versionId, items);

            assertThat(OrderingItems.keys(given)).isNotEqualTo(OrderingItems.keys(items));
            assertThat(given).containsExactlyInAnyOrderElementsOf(items);
            assertThat(OrderingItems.learnerOrder(versionId, items)).isEqualTo(given);
        }
    }

    @Test
    void interchangeableItemsReadTheSame() {
        UUID versionId = UUID.randomUUID();
        List<QuestionOption> book = items(versionId, List.of("b", "o", "O ", "k"));
        List<QuestionOption> swapped = List.of(book.get(0), book.get(2), book.get(1), book.get(3));
        assertThat(OrderingItems.keys(swapped)).isEqualTo(OrderingItems.keys(book));
        // A picture makes an item different from one that only shares its label.
        QuestionOption pictured = QuestionOption.create(versionId, "o", false, 9).withMedia(UUID.randomUUID(), null);
        assertThat(OrderingItems.key(pictured)).isNotEqualTo(OrderingItems.key(book.get(1)));
    }

    private static List<QuestionOption> items(UUID versionId, List<String> labels) {
        List<QuestionOption> items = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            QuestionOption item = QuestionOption.create(versionId, labels.get(i), false, i);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            items.add(item);
        }
        return items;
    }

}
