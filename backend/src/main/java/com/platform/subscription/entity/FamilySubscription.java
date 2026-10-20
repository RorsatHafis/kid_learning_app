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
        requireAdminManaged();
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

    /**
     * Y43: what a confirmed payment gives - the plan until {@code periodEnd}, recorded as coming from the
     * payment (provider and our order reference), so access and billing can't drift apart. Only
     * PaymentService calls this, and only for an order that has just succeeded.
     */
    public static FamilySubscription fromPayment(UUID familyId, UUID planId, Instant now, Instant periodEnd, String provider,
                                              String reference) {

        Assert.notNull(familyId, "familyId must not be null");
        FamilySubscription subscription = new FamilySubscription();
        subscription.familyId = familyId;
        subscription.applyPayment(planId, now, periodEnd, provider, reference);
        return subscription;

    }

    /**
     * Y43: a confirmed payment replaces or extends what the family had. Renewing the same paid plan keeps
     * its start; anything else starts now. The previous state is audited by the caller.
     */
    public void applyPayment(UUID planId, Instant now, Instant periodEnd, String provider, String reference) {

        Assert.notNull(planId, "planId must not be null");
        Assert.hasText(provider, "provider must not be blank");
        Assert.hasText(reference, "reference must not be blank");
        boolean renewal = source == SubscriptionSource.PROVIDER && planId.equals(this.planId) && grantsAccessAt(now);
        this.planId = planId;
        this.status = SubscriptionStatus.ACTIVE;
        this.source = SubscriptionSource.PROVIDER;
        this.startedAt = renewal ? this.startedAt : now;
        this.currentPeriodEnd = periodEnd;
        this.cancelAtPeriodEnd = false;
        this.provider = provider;
        this.providerReference = reference;

    }

    /**
     * Y43: a refunded payment's time is taken back - the period ends at {@code newEnd}, or now if that has
     * passed. Nothing else changes: no other payment's time is touched.
     */
    public void reverseRefund(Instant newEnd, Instant now) {

        if (source != SubscriptionSource.PROVIDER) {
            throw new IllegalStateException("This plan wasn't paid for, so there is nothing to refund on it");
        }

        if (newEnd == null || !newEnd.isAfter(now)) {
            this.status = SubscriptionStatus.EXPIRED;
            this.currentPeriodEnd = now;
        } else {
            this.currentPeriodEnd = newEnd;
        }

    }

    /** Ends access now (an administrator ending a grant). */
    public void end(Instant now) {
        requireAdminManaged();
        if (status == SubscriptionStatus.EXPIRED) {
            throw new IllegalStateException("This subscription has already ended");
        }
        this.status = SubscriptionStatus.EXPIRED;
        this.currentPeriodEnd = now;
        this.cancelAtPeriodEnd = false;
    }

    /**
     * An administrator moves the end of a grant that is still in force: later, earlier, or
     * open-ended ({@code null}). The plan and its start stay; a plan that has already ended
     * needs a new grant instead.
     */
    public void changePeriodEnd(Instant periodEnd, Instant now) {
        requireAdminManaged();
        if (!grantsAccessAt(now)) {
            throw new IllegalStateException("This plan has already ended: give the family a plan instead");
        }
        if (periodEnd != null && !periodEnd.isAfter(now)) {
            throw new IllegalArgumentException("The new end must be in the future; to stop the plan now, end it");
        }
        this.currentPeriodEnd = periodEnd;
    }

    /**
     * A provider's subscription follows the provider's events (payments, renewals, the family
     * cancelling with them); an administrator changing it here would leave billing and access
     * disagreeing. None exists until a provider is integrated (PD-09).
     */
    private void requireAdminManaged() {
        if (source == SubscriptionSource.PROVIDER) {
            throw new IllegalStateException("This subscription is managed by the payment provider; change it there");
        }
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
