package com.platform.subscription.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.Locale;

/**
 * What a family can have (V42). Its entitlements are set while it is a DRAFT; activating
 * freezes them, so a family never finds its plan changed underneath it. The owner changes
 * the offer by drafting and activating another plan. Price is optional until the owner
 * decides one (PD-09).
 */
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan extends AuditableEntity {

    @Column(name = "code", nullable = false, updatable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPlan;

    @Column(name = "self_service", nullable = false)
    private boolean selfService;

    @Column(name = "price_amount_minor")
    private Long priceAmountMinor;

    @Column(name = "price_currency", length = 3)
    private String priceCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", length = 10)
    private BillingInterval billingInterval;

    protected SubscriptionPlan() {
        // JPA
    }

    public static SubscriptionPlan draft(String code, String name, String description) {
        Assert.hasText(code, "code must not be blank");
        Assert.hasText(name, "name must not be blank");
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.code = code;
        plan.name = name.strip();
        plan.description = description == null || description.isBlank() ? null : description.strip();
        plan.status = PlanStatus.DRAFT;
        return plan;
    }

    public void revise(String name, String description, boolean selfService, Long priceAmountMinor, String priceCurrency,
                       BillingInterval billingInterval) {
        requireDraft("revised");
        Assert.hasText(name, "name must not be blank");
        boolean anyPrice = priceAmountMinor != null || priceCurrency != null || billingInterval != null;
        boolean fullPrice = priceAmountMinor != null && priceCurrency != null && billingInterval != null;
        if (anyPrice && !fullPrice) {
            throw new IllegalArgumentException("A price needs an amount, a currency and a billing interval (or none of them)");
        }
        if (priceAmountMinor != null && priceAmountMinor < 0) {
            throw new IllegalArgumentException("A price can't be negative");
        }
        String currency = priceCurrency == null ? null : priceCurrency.strip().toUpperCase(Locale.ROOT);
        if (currency != null) {
            // The amount is in this currency's minor units (V42), so it must be a real ISO 4217 code.
            try {
                java.util.Currency.getInstance(currency);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("\"" + currency + "\" isn't a currency code (use ISO 4217, such as USD or KHR)");
            }
        }
        this.name = name.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
        this.selfService = selfService;
        this.priceAmountMinor = priceAmountMinor;
        this.priceCurrency = currency;
        this.billingInterval = billingInterval;
    }

    public void activate() {
        requireDraft("activated");
        this.status = PlanStatus.ACTIVE;
    }

    /** No new family can be given it; families already on it keep it until their subscription ends. */
    public void retire() {
        if (status != PlanStatus.ACTIVE) {
            throw new IllegalStateException("Only an active plan can be retired");
        }
        if (defaultPlan) {
            throw new IllegalStateException("The default plan can't be retired: make another plan the default first");
        }
        this.status = PlanStatus.RETIRED;
    }

    public void makeDefault() {
        if (status != PlanStatus.ACTIVE) {
            throw new IllegalStateException("Only an active plan can be the default");
        }
        this.defaultPlan = true;
    }

    public void clearDefault() {
        this.defaultPlan = false;
    }

    public void requireDraft(String action) {
        if (status != PlanStatus.DRAFT) {
            throw new IllegalStateException("Only a draft plan can be " + action + "; this one is " + status.name().toLowerCase());
        }
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public boolean isDefaultPlan() {
        return defaultPlan;
    }

    public boolean isSelfService() {
        return selfService;
    }

    public Long getPriceAmountMinor() {
        return priceAmountMinor;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public BillingInterval getBillingInterval() {
        return billingInterval;
    }
}
