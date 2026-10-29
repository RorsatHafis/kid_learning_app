package com.platform.subscription.service;

import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.PlanEntitlement;
import com.platform.subscription.entity.PlanStatus;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.PlanEntitlementRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M6: the real plans that would lift a family's limit - for a refusal to say what unlocks it instead of a dead end.
 * Only plans a family may actually choose (active, self-service) whose limit for that thing is higher or open; a
 * price only when the plan has one. Nothing is invented: no plan, no offer.
 *
 * <p>Families only. A school's limit can be reached by a family's action (giving a school access to a child), and a
 * school's plans are its principal's business - they see them on the school's own plan page.
 */
@Service
public class PlanOffers {

    /** At most this many: the smallest steps up first, so the notice stays a short list however many plans exist. */
    static final int MAX_OFFERS = 3;

    /** A plan that includes more of {@code key}; {@code limit} null means no limit. */
    public record Offer(UUID planId, String name, Integer limit, Long priceAmountMinor, String priceCurrency, String billingInterval) {
    }

    private final SubscriptionPlanRepository plans;
    private final PlanEntitlementRepository entitlements;

    public PlanOffers(SubscriptionPlanRepository plans, PlanEntitlementRepository entitlements) {
        this.plans = plans;
        this.entitlements = entitlements;
    }

    @Transactional(readOnly = true)
    public List<Offer> unlocking(EntitlementKey key, int currentLimit) {
        if (key.audience() != PlanAudience.FAMILY) {
            return List.of();
        }
        List<SubscriptionPlan> choosable = plans.findAllByOrderByCreatedAtAsc().stream()
                .filter(plan -> plan.getAudience() == PlanAudience.FAMILY)
                .filter(plan -> plan.offeredAt(java.time.Instant.now()) && plan.isSelfService())
                .toList();
        if (choosable.isEmpty()) {
            return List.of();
        }
        Map<UUID, Optional<Integer>> limits = entitlements.findByPlanIdIn(choosable.stream().map(SubscriptionPlan::getId).toList()).stream()
                .filter(e -> e.getKey() == key)
                .collect(Collectors.toMap(PlanEntitlement::getPlanId, e -> Optional.ofNullable(e.getLimitValue())));
        return choosable.stream()
                // A plan without this limit set includes it without limit.
                .filter(plan -> limits.getOrDefault(plan.getId(), Optional.empty()).map(limit -> limit > currentLimit).orElse(true))
                .map(plan -> new Offer(plan.getId(), plan.getName(), limits.getOrDefault(plan.getId(), Optional.empty()).orElse(null),
                        plan.getPriceAmountMinor(), plan.getPriceCurrency(),
                        plan.getBillingInterval() == null ? null : plan.getBillingInterval().name()))
                .sorted(Comparator.comparing((Offer offer) -> offer.limit() == null ? Integer.MAX_VALUE : offer.limit())
                        .thenComparing(offer -> offer.priceAmountMinor() == null ? 0L : offer.priceAmountMinor()))
                .limit(MAX_OFFERS)
                .toList();
    }

}
