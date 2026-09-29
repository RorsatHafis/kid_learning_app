package com.platform.subscription.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/** One lever of a plan: its limit, or unlimited ({@code limitValue == null}). */
@Entity
@Table(name = "plan_entitlements")
public class PlanEntitlement extends AuditableEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_key", nullable = false, updatable = false, length = 60)
    private EntitlementKey key;

    @Column(name = "limit_value")
    private Integer limitValue;

    protected PlanEntitlement() {
        // JPA
    }

    public static PlanEntitlement of(UUID planId, EntitlementKey key, Integer limitValue) {
        Assert.notNull(planId, "planId must not be null");
        Assert.notNull(key, "key must not be null");
        PlanEntitlement entitlement = new PlanEntitlement();
        entitlement.planId = planId;
        entitlement.key = key;
        entitlement.setLimit(limitValue);
        return entitlement;
    }

    public void setLimit(Integer limitValue) {
        if (limitValue != null && limitValue < 0) {
            throw new IllegalArgumentException("A limit can't be negative");
        }
        this.limitValue = limitValue;
    }

    public UUID getPlanId() {
        return planId;
    }

    public EntitlementKey getKey() {
        return key;
    }

    public Integer getLimitValue() {
        return limitValue;
    }
}
