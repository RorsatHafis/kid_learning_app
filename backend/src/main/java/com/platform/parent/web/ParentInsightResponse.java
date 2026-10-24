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
        return new ParentInsightResponse(insight.getId(), insight.getInsightType(),
                InsightText.translate(insight.getHeadline(), language, childName),
                InsightText.translate(insight.getDetail(), language, childName),
                insight.getSupportingData(), insight.getOccurredAt());
    }
}
