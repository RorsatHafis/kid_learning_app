package com.platform.payment.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * One line of the payment ledger (V62, append-only): what happened to an order, from which state to which,
 * by whom, and - for a provider's message - its id, so a repeated callback is recognised. The detail holds
 * safe values only: never card data, secrets or raw provider payloads.
 */
@Entity
@Table(name = "payment_events")
public class PaymentEvent extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 20)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", updatable = false, length = 20)
    private PaymentStatus toStatus;

    @Column(name = "actor_account_id", updatable = false)
    private UUID actorAccountId;

    @Column(name = "provider_event_id", updatable = false, length = 200)
    private String providerEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", nullable = false, updatable = false)
    private Map<String, Object> detail;

    // Set here (at the database's precision) so a line just written reads back with its time.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentEvent() {

        // JPA

    }

    public static PaymentEvent of(UUID orderId, String eventType, PaymentStatus from, PaymentStatus to, UUID actor,
                                  String providerEventId, Map<String, Object> detail) {

        PaymentEvent event = new PaymentEvent();
        event.orderId = orderId;
        event.eventType = eventType;
        event.fromStatus = from;
        event.toStatus = to;
        event.actorAccountId = actor;
        event.providerEventId = providerEventId;
        event.detail = detail == null ? Map.of() : detail;
        event.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return event;

    }

    public UUID getOrderId() {

        return orderId;

    }

    public String getEventType() {

        return eventType;

    }

    public PaymentStatus getFromStatus() {

        return fromStatus;

    }

    public PaymentStatus getToStatus() {

        return toStatus;

    }

    public UUID getActorAccountId() {

        return actorAccountId;

    }

    public String getProviderEventId() {

        return providerEventId;

    }

    public Map<String, Object> getDetail() {

        return detail;

    }

    public Instant getCreatedAt() {

        return createdAt;

    }

}
