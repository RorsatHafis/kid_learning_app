package com.platform.parent.web;

import com.platform.parent.entity.ParentInsight;
import com.platform.parent.entity.ParentInsightType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A parent insight as the family sees it, in the reader's language, with how strong the evidence behind it is.
 */
public record ParentInsightResponse(
        UUID id,
        ParentInsightType insightType,
        String headline,
        String detail,
        Map<String, Object> supportingData,
        Instant occurredAt
) {
    /** As recorded (English): the family's data export keeps the record itself. */
    public static ParentInsightResponse from(ParentInsight insight) {
        return from(insight, null);
    }

    /** For reading on screen: the sentence in the reader's interface language (InsightText). */
    public static ParentInsightResponse from(ParentInsight insight, String language) {
        return from(insight, language, null);
    }

    /** As {@link #from(ParentInsight, String)}, naming the child for a reader outside the family (a caregiver). */
    public static ParentInsightResponse from(ParentInsight insight, String language, String childName) {
        return from(insight, language, childName, null);
    }

    /** As {@link #from(ParentInsight, String, String)}, with the skill or chapter it names in the reader's language. */
    public static ParentInsightResponse from(ParentInsight insight, String language, String childName, InsightNames names) {
        String headline = InsightText.translate(insight.getHeadline(), language, childName);
        String detail = InsightText.translate(insight.getDetail(), language, childName);
        return new ParentInsightResponse(insight.getId(), insight.getInsightType(),
                names == null ? headline : names.apply(insight, headline, language),
                names == null ? detail : names.apply(insight, detail, language),
                insight.getSupportingData(), insight.getOccurredAt());
    }
}
