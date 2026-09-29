package com.platform.subscription.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * A family's current subscription (V42: one per family; earlier states are in the audit log).
 * Every transition goes through this entity, whichever way it arrives: an administrator's
 * grant today, a payment provider's event once one is integrated.
 */
@Entity
@Table(name = "family_subscriptions")
public class FamilySubscription extends AuditableEntity {

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SubscriptionSource source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "provider", length = 40)
    private String provider;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "note", length = 500)
    private String note;

    protected FamilySubscription() {
        // JPA
    }

    /** An administrator gives a family a plan, open-ended or until {@code periodEnd}. */
    public static FamilySubscription grant(UUID familyId, UUID planId, Instant now, Instant periodEnd, String note) {
        Assert.notNull(familyId, "familyId must not be null");
        FamilySubscription subscription = new FamilySubscription();
        subscription.familyId = familyId;
        subscription.regrant(planId, now, periodEnd, note);
        return subscription;
    }

    /** A new grant replaces whatever the family had (the previous state is audited by the caller). */
    public void regrant(UUID planId, Instant now, Instant periodEnd, String note) {
        Assert.notNull(planId, "planId must not be null");
        Assert.notNull(now, "now must not be null");
        if (periodEnd != null && !periodEnd.isAfter(now)) {
            throw new IllegalArgumentException("A grant must end in the future");
        }
        this.planId = planId;
        this.status = SubscriptionStatus.ACTIVE;
        this.source = SubscriptionSource.ADMIN_GRANT;
        this.startedAt = now;
        this.currentPeriodEnd = periodEnd;
        this.cancelAtPeriodEnd = false;
        this.provider = null;
        this.providerReference = null;
        this.note = note == null || note.isBlank() ? null : note.strip();
    }

    /** Ends access now (an administrator ending a grant). */
    public void end(Instant now) {
        if (status == SubscriptionStatus.EXPIRED) {
            throw new IllegalStateException("This subscription has already ended");
        }
        this.status = SubscriptionStatus.EXPIRED;
        this.currentPeriodEnd = now;
        this.cancelAtPeriodEnd = false;
    }

    /** Whether it gives the family its plan at {@code at}. */
    public boolean grantsAccessAt(Instant at) {
        boolean withinPeriod = currentPeriodEnd == null || currentPeriodEnd.isAfter(at);
        return switch (status) {
            case TRIALING, ACTIVE, PAST_DUE -> withinPeriod;
            case CANCELED -> currentPeriodEnd != null && currentPeriodEnd.isAfter(at);
            case EXPIRED -> false;
        };
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public SubscriptionSource getSource() {
        return source;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getNote() {
        return note;
    }
}
