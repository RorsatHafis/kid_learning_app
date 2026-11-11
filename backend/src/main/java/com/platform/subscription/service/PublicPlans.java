package com.platform.subscription.service;

import com.platform.subscription.entity.PlanEntitlement;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.PlanEntitlementRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * P-1 / LAND-1: the plans the public pages show, straight from the plan records - so a price changed in Admin ->
 * Plans is the price the landing page shows, with no second copy to keep in step. What is on offer now: each
 * audience's default plan and the plans people can choose themselves, active and inside their offer dates (the
 * same rule a family's own plan page uses). Names and descriptions in the reader's language where the administrator
 * wrote them in Khmer (V82); no internal fields (status, ids of drafts, subscriptions).
 */
@Service
public class PublicPlans {

    public record Limit(String key, Integer limit) {
    }

    /**
     * {@code amountMinor} null: nothing to pay. {@code limits}: what the plan includes; a null limit is unlimited.
     * {@code subjects} (family plans, 2026-11-11): the codes of the subjects families can learn now that the plan
     * includes (V89); null for a school plan. {@code chooseable}: it can be chosen before registering (the free
     * default, or a priced plan people choose themselves).
     */
    public record PublicPlan(String code, String audience, String name, String description, boolean isDefault,
                             Long amountMinor, String currency, String billingInterval, List<Limit> limits,
                             List<String> subjects, boolean chooseable) {
    }

    private final SubscriptionPlanRepository plans;
    private final PlanEntitlementRepository entitlements;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    private com.platform.curriculum.service.PublicSubjects publicSubjects;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    public PublicPlans(SubscriptionPlanRepository plans, PlanEntitlementRepository entitlements) {
        this(plans, entitlements, Clock.systemUTC());
    }

    PublicPlans(SubscriptionPlanRepository plans, PlanEntitlementRepository entitlements, Clock clock) {
        this.plans = plans;
        this.entitlements = entitlements;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PublicPlan> offered(String language) {
        Instant now = clock.instant();
        List<SubscriptionPlan> shown = plans.findAllByOrderByCreatedAtAsc().stream()
                .filter(plan -> plan.offeredAt(now) && (plan.isDefaultPlan() || plan.isSelfService()))
                .toList();
        if (shown.isEmpty()) {
            return List.of();
        }
        // The subjects families can learn now, and per family plan the ones it leaves out (V89: no rows - every plan).
        List<String> learnable = publicSubjects == null ? List.of() : publicSubjects.offered().stream()
                .filter(com.platform.curriculum.service.PublicSubjects.PublicSubject::available)
                .map(com.platform.curriculum.service.PublicSubjects.PublicSubject::code).toList();
        Map<UUID, java.util.Set<String>> outside = new java.util.HashMap<>();
        if (jdbc != null) {
            jdbc.query("""
                    select p.id as plan_id, s.code from subscription_plans p
                    join subjects s on exists (select 1 from subject_plans x where x.subject_id = s.id)
                    where not exists (select 1 from subject_plans x where x.subject_id = s.id and x.plan_id = p.id)
                    """, rs -> {
                outside.computeIfAbsent(rs.getObject("plan_id", UUID.class), id -> new java.util.HashSet<>()).add(rs.getString("code"));
            });
        }
        Map<UUID, List<PlanEntitlement>> byPlan = entitlements.findByPlanIdIn(shown.stream().map(SubscriptionPlan::getId).toList())
                .stream().collect(Collectors.groupingBy(PlanEntitlement::getPlanId));
        return shown.stream()
                // Per audience: the default first, then by price (nothing to pay first).
                .sorted(Comparator.comparing((SubscriptionPlan plan) -> plan.getAudience().name())
                        .thenComparing(plan -> !plan.isDefaultPlan())
                        .thenComparing(plan -> plan.getPriceAmountMinor() == null ? 0L : plan.getPriceAmountMinor()))
                .map(plan -> new PublicPlan(plan.getCode(), plan.getAudience().name(), plan.nameIn(language),
                        plan.descriptionIn(language), plan.isDefaultPlan(), plan.getPriceAmountMinor(), plan.getPriceCurrency(),
                        plan.getBillingInterval() == null ? null : plan.getBillingInterval().name(),
                        byPlan.getOrDefault(plan.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(e -> e.getKey().name()))
                                .map(e -> new Limit(e.getKey().name(), e.getLimitValue())).toList(),
                        plan.getAudience() == com.platform.subscription.entity.PlanAudience.FAMILY
                                ? learnable.stream().filter(code -> !outside.getOrDefault(plan.getId(), java.util.Set.of()).contains(code)).toList()
                                : null,
                        com.platform.subscription.choice.PlanChoices.chooseable(plan, now)))
                .toList();
    }
}
