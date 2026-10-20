package com.platform.payment.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.subscription.entity.BillingInterval;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One attempt to pay for one plan (V62). It freezes the plan, price, currency, period and limits offered
 * when checkout began, so what was bought never changes with the plan. Every state change goes through
 * the methods below, which allow only the transitions the payment lifecycle permits; the service appends
 * each one to the payment ledger.
 */
@Entity
@Table(name = "payment_orders")
public class PaymentOrder extends AuditableEntity {

    @Column(name = "reference", nullable = false, updatable = false, length = 20)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, updatable = false, length = 10)
    private PaymentCustomerType customerType;

    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "school_id", updatable = false)
    private UUID schoolId;

    @Column(name = "created_by_account_id")
    private UUID createdByAccountId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "plan_code", nullable = false, updatable = false, length = 60)
    private String planCode;

    @Column(name = "plan_name", nullable = false, updatable = false, length = 120)
    private String planName;

    @Column(name = "plan_version", nullable = false, updatable = false)
    private long planVersion;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, updatable = false, length = 10)
    private BillingInterval billingInterval;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limits_snapshot", nullable = false, updatable = false)
    private Map<String, Integer> limitsSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 20)
    private PaymentProviderType provider;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "customer_note", length = 300)
    private String customerNote;

    protected PaymentOrder() {

        // JPA

    }

    /** What was offered when checkout began: the plan as it was, with its price and limits. */
    public record Offer(UUID planId, String planCode, String planName, long planVersion, long amountMinor, String currency,
                        BillingInterval interval, Map<String, Integer> limits) {
    }

    /** A new order, pending until its provider or a reviewer settles it. */
    public static PaymentOrder open(String reference, PaymentCustomerType customerType, UUID familyId, UUID schoolId,
                                    UUID createdBy, Offer offer, PaymentProviderType provider, String idempotencyKey,
                                    Instant expiresAt) {

        Assert.hasText(reference, "reference must not be blank");
        Assert.isTrue(offer.amountMinor() > 0, "a payment needs a positive amount");
        PaymentOrder order = new PaymentOrder();
        order.reference = reference;
        order.customerType = customerType;
        order.familyId = familyId;
        order.schoolId = schoolId;
        order.createdByAccountId = createdBy;
        order.planId = offer.planId();
        order.planCode = offer.planCode();
        order.planName = offer.planName();
        order.planVersion = offer.planVersion();
        order.amountMinor = offer.amountMinor();
        order.currency = offer.currency();
        order.billingInterval = offer.interval();
        order.limitsSnapshot = offer.limits();
        order.provider = provider;
        order.idempotencyKey = idempotencyKey;
        order.expiresAt = expiresAt;
        order.status = PaymentStatus.PENDING;
        return order;

    }

    /** Money confirmed - by the provider's own answer or a reviewer against the real receipt - and the access it gives. */
    public void succeed(Instant now, String providerReference, Instant periodStart, Instant periodEnd) {

        if (!status.canStillSucceed()) {
            throw new IllegalStateException("This payment has already been settled");
        }

        Assert.isTrue(periodEnd.isAfter(periodStart), "a paid period must end after it starts");
        this.status = PaymentStatus.SUCCEEDED;
        this.completedAt = now;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.failureReason = null;
        if (providerReference != null && !providerReference.isBlank()) {
            this.providerReference = providerReference.strip();
        }

    }

    /** The provider declined it, or a reviewer found no money arrived. */
    public void fail(Instant now, String reason) {

        requirePending("Only a payment in progress can be marked failed");
        this.status = PaymentStatus.FAILED;
        this.completedAt = now;
        this.failureReason = reason;

    }

    /** The customer (or the provider, on their behalf) stopped before paying. */
    public void cancel(Instant now) {

        requirePending("Only a payment in progress can be cancelled");
        this.status = PaymentStatus.CANCELLED;
        this.completedAt = now;

    }

    /** Nobody paid in time. */
    public void expire(Instant now) {

        requirePending("Only a payment in progress can expire");
        this.status = PaymentStatus.EXPIRED;
        this.completedAt = now;

    }

    /** The money went back: the access it gave is taken back by the caller (exactly this order's period). */
    public void refund(Instant now) {

        if (status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Only a payment that succeeded can be refunded");
        }

        this.status = PaymentStatus.REFUNDED;
        this.completedAt = now;

    }

    /** What the customer says they paid with - recorded for the reviewer, never treated as proof. */
    public void noteFromCustomer(String note) {

        requirePending("Only a payment in progress can be changed");
        this.customerNote = note == null || note.isBlank() ? null : note.strip();

    }

    public boolean isPastExpiry(Instant now) {

        return status == PaymentStatus.PENDING && !expiresAt.isAfter(now);

    }

    private void requirePending(String refusal) {

        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(refusal);
        }

    }

    public String getReference() {

        return reference;

    }

    public PaymentCustomerType getCustomerType() {

        return customerType;

    }

    public UUID getFamilyId() {

        return familyId;

    }

    public UUID getSchoolId() {

        return schoolId;

    }

    public UUID getCreatedByAccountId() {

        return createdByAccountId;

    }

    public UUID getPlanId() {

        return planId;

    }

    public String getPlanCode() {

        return planCode;

    }

    public String getPlanName() {

        return planName;

    }

    public long getPlanVersion() {

        return planVersion;

    }

    public long getAmountMinor() {

        return amountMinor;

    }

    public String getCurrency() {

        return currency;

    }

    public BillingInterval getBillingInterval() {

        return billingInterval;

    }

    public Map<String, Integer> getLimitsSnapshot() {

        return limitsSnapshot;

    }

    public PaymentProviderType getProvider() {

        return provider;

    }

    public String getProviderReference() {

        return providerReference;

    }

    public PaymentStatus getStatus() {

        return status;

    }

    public String getIdempotencyKey() {

        return idempotencyKey;

    }

    public Instant getExpiresAt() {

        return expiresAt;

    }

    public Instant getCompletedAt() {

        return completedAt;

    }

    public Instant getPeriodStart() {

        return periodStart;

    }

    public Instant getPeriodEnd() {

        return periodEnd;

    }

    public String getFailureReason() {

        return failureReason;

    }

    public String getCustomerNote() {

        return customerNote;

    }

}
