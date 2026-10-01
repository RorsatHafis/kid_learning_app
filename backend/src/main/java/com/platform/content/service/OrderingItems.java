package com.platform.content.service;

import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.content.entity.QuestionOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * ORDERING questions (X11): the items are a version's options and their display order is the
 * answer. Shared by publishing checks, scoring and the learner's view, so the three agree.
 *
 * <p>Two items that read and look the same (the two o's in "b o o k") are interchangeable: an
 * order is right when the items read, in turn, as the correct order does - not when it happens
 * to use one particular "o" first.
 */
public final class OrderingItems {

    public static final int MIN_ITEMS = 3;
    public static final int MAX_ITEMS = 8;

    private OrderingItems() {
    }

    /** What a child sees of an item: its text, picture and sound. */
    public static String key(QuestionOption item) {
        return item.getLabel().strip().toLowerCase(Locale.ROOT) + "|" + Objects.toString(item.getImageAssetId(), "")
                + "|" + Objects.toString(item.getAudioAssetId(), "");
    }

    public static List<String> keys(List<QuestionOption> items) {
        return items.stream().map(OrderingItems::key).toList();
    }

    /**
     * The order a child is given the items in: derived from the ids, so stable for the version
     * (deterministic, CLAUDE.md §14) and unrelated to the answer - and never already the answer,
     * which would leave nothing to do. Rotating by one changes any order that has two different
     * items, which publishing requires.
     */
    public static List<QuestionOption> learnerOrder(UUID questionVersionId, List<QuestionOption> itemsInAnswerOrder) {
        List<QuestionOption> shuffled = new ArrayList<>(itemsInAnswerOrder);
        shuffled.sort(Comparator.comparing(o -> RequestFingerprint.sha256Hex(questionVersionId + ":order:" + o.getId())));
        if (keys(shuffled).equals(keys(itemsInAnswerOrder))) {
            Collections.rotate(shuffled, 1);
        }
        return shuffled;
    }

}
