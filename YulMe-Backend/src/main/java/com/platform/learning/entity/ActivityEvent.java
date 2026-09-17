package com.platform.learning.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * Fine-grained telemetry within an attempt (started/paused/hint_requested/...).
 * {@code eventType} stays an open string, matching {@code audit_events.action}'s
 * reasoning (V3) - genuinely open-ended vocabulary many future features will add
 * to. Immutable, matching the {@code prevent_row_mutation()} trigger on
 * {@code activity_events} (V14 migration).
 */
@Entity
@Table(name = "activity_events")
public class ActivityEvent extends ImmutableEvent {

    @Column(name = "activity_attempt_id", nullable = false, updatable = false)
    private UUID activityAttemptId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private Map<String, Object> payload;

    protected ActivityEvent() {
        // JPA
    }

    private ActivityEvent(UUID activityAttemptId, String eventType, Map<String, Object> payload) {
        this.activityAttemptId = activityAttemptId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public static ActivityEvent record(UUID activityAttemptId, String eventType, Map<String, Object> payload) {
        Assert.notNull(activityAttemptId, "activityAttemptId must not be null");
        Assert.hasText(eventType, "eventType must not be blank");
        return new ActivityEvent(activityAttemptId, eventType, payload == null ? Map.of() : Map.copyOf(payload));
    }

    public UUID getActivityAttemptId() {
        return activityAttemptId;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

}