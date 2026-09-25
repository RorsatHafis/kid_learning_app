package com.platform.parent.web;

import com.platform.parent.entity.ParentInsight;
import com.platform.parent.entity.ParentInsightType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ParentInsightResponse(
        UUID id,
        ParentInsightType insightType,
        String headline,
        String detail,
        Map<String, Object> supportingData,
        Instant occurredAt
) {
    public static ParentInsightResponse from(ParentInsight insight) {
        return new ParentInsightResponse(insight.getId(), insight.getInsightType(), insight.getHeadline(),
                insight.getDetail(), insight.getSupportingData(), insight.getOccurredAt());
    }
}
