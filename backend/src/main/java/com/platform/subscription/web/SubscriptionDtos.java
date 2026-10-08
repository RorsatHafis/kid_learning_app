package com.platform.subscription.web;

import com.platform.subscription.entity.BillingInterval;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.FamilySubscription;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.service.EntitlementService;
import com.platform.subscription.service.SubscriptionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record Price(long amountMinor, String currency, BillingInterval interval) {
    }

    public record PlanSummary(UUID id, String code, String name, String description, String status, boolean isDefault,
                              boolean selfService, Price price) {

        static PlanSummary of(SubscriptionPlan plan) {
            Price price = plan.getPriceAmountMinor() == null ? null
                    : new Price(plan.getPriceAmountMinor(), plan.getPriceCurrency(), plan.getBillingInterval());
            return new PlanSummary(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), plan.getStatus().name(),
                    plan.isDefaultPlan(), plan.isSelfService(), price);
        }
    }

    /** {@code limit} null = unlimited; {@code used} only in a family's view. */
    public record Entitlement(EntitlementKey key, Integer limit, Integer used) {
    }

    private static List<Entitlement> entitlementList(Map<EntitlementKey, Integer> limits, Map<EntitlementKey, Integer> usage) {
        return Arrays.stream(EntitlementKey.values())
                .filter(limits::containsKey)
                .map(key -> new Entitlement(key, limits.get(key), usage == null ? null : usage.get(key)))
                .toList();
    }

    public record PlanResponse(PlanSummary plan, List<Entitlement> entitlements, long familiesOnPlan) {

        static PlanResponse of(SubscriptionService.PlanView view) {
            return new PlanResponse(PlanSummary.of(view.plan()), entitlementList(view.limits(), null), view.familiesOnPlan());
        }
    }

    /** An operator's view of a subscription; {@code id} is its audit-log resource. */
    public record SubscriptionState(UUID id, String status, String source, Instant startedAt, Instant currentPeriodEnd,
                                    boolean cancelAtPeriodEnd, String note, boolean grantsAccess) {

        static SubscriptionState of(FamilySubscription subscription, boolean grantsAccess) {
            return new SubscriptionState(subscription.getId(), subscription.getStatus().name(), subscription.getSource().name(),
                    subscription.getStartedAt(), subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(),
                    subscription.getNote(), grantsAccess);
        }
    }

    /** The family's own view of its subscription: no operator note (it is for administrators only). */
    public record OwnSubscriptionState(String status, String source, Instant startedAt, Instant currentPeriodEnd,
                                       boolean cancelAtPeriodEnd, boolean grantsAccess) {

        static OwnSubscriptionState of(FamilySubscription subscription, boolean grantsAccess) {
            return new OwnSubscriptionState(subscription.getStatus().name(), subscription.getSource().name(),
                    subscription.getStartedAt(), subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(), grantsAccess);
        }
    }

    public record PlanOfferResponse(PlanSummary plan, List<Entitlement> entitlements) {

        static PlanOfferResponse of(SubscriptionService.PlanOffer offer) {
            return new PlanOfferResponse(PlanSummary.of(offer.plan()), entitlementList(offer.limits(), null));
        }
    }

    /**
     * What a parent sees of the family's plan.
     *
     * @param afterwards the plan that applies once the subscription ends, when an end is set
     * @param choices    other plans families may choose themselves once payments exist (PD-09)
     */
    /** The family's open request for another plan (Y11). */
    public record OpenPlanRequest(UUID planId, String planName, java.time.Instant requestedAt) {
    }

    public record OwnFamilyPlanResponse(PlanSummary plan, boolean onDefaultPlan, OwnSubscriptionState subscription,
                                        List<Entitlement> entitlements, PlanOfferResponse afterwards,
                                        List<PlanOfferResponse> choices, OpenPlanRequest request) {

        public static OwnFamilyPlanResponse of(SubscriptionService.FamilyPlanView view) {
            EntitlementService.FamilyPlan effective = view.effective();
            return new OwnFamilyPlanResponse(PlanSummary.of(effective.plan()), !effective.fromSubscription(),
                    effective.subscription().map(s -> OwnSubscriptionState.of(s, effective.fromSubscription())).orElse(null),
                    entitlementList(effective.limits(), view.usage()),
                    view.afterwards().map(PlanOfferResponse::of).orElse(null),
                    view.choices().stream().map(PlanOfferResponse::of).toList(),
                    view.request().map(r -> new OpenPlanRequest(r.planId(), r.planName(), r.requestedAt())).orElse(null));
        }
    }

    /**
     * @param onDefaultPlan the family has no subscription granting a plan now, so the default applies
     * @param subscription  its subscription row, when it has one (possibly ended)
     */
    public record FamilyPlanResponse(PlanSummary plan, boolean onDefaultPlan, SubscriptionState subscription,
                                     List<Entitlement> entitlements) {

        static FamilyPlanResponse of(EntitlementService.FamilyPlan effective, Map<EntitlementKey, Integer> usage) {
            return new FamilyPlanResponse(PlanSummary.of(effective.plan()), !effective.fromSubscription(),
                    effective.subscription().map(s -> SubscriptionState.of(s, effective.fromSubscription())).orElse(null),
                    entitlementList(effective.limits(), usage));
        }
    }

    public record ParentResponse(UUID accountId, String email) {
    }

    public record FamilyResponse(UUID familyId, String familyName, List<ParentResponse> parents, int childCount,
                                 FamilyPlanResponse plan) {

        static FamilyResponse of(SubscriptionService.FamilyView view) {
            return new FamilyResponse(view.familyId(), view.familyName(),
                    view.parents().stream().map(p -> new ParentResponse(p.accountId(), p.email())).toList(), view.childCount(),
                    FamilyPlanResponse.of(view.effective(), view.usage()));
        }
    }

    public record CreatePlanRequest(@NotBlank @Size(max = 60) String code, @NotBlank @Size(max = 120) String name,
                                    @Size(max = 1000) String description) {
    }

    /** {@code limits}: every key to set; a null value is unlimited. */
    public record RevisePlanRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
                                    boolean selfService, Long priceAmountMinor, @Size(min = 3, max = 3) String priceCurrency,
                                    BillingInterval billingInterval, @NotNull Map<EntitlementKey, Integer> limits) {
    }

    public record GrantRequest(@NotNull UUID planId, Instant periodEnd, @Size(max = 500) String note) {
    }

    /** {@code periodEnd} null = open-ended. */
    public record PeriodRequest(Instant periodEnd, @NotBlank @Size(max = 500) String reason) {
    }

    public record EndRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
