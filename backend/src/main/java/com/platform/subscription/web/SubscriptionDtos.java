package com.platform.subscription.web;

import com.platform.subscription.entity.BillingInterval;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.FamilySubscription;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.SchoolSubscription;
import com.platform.subscription.service.SchoolEntitlementService;
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

/**
 * Requests and responses for plans and subscriptions. A family's or principal's own view leaves out operator
 * notes; prices are in minor units with their currency.
 */
public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record Price(long amountMinor, String currency, BillingInterval interval) {
    }

    /** A plan's words in Khmer (V82): either may be null (not translated). */
    public record PlanKhmer(String name, String description) {
    }

    /** A plan's Khmer in a request: blank = not translated; leaving {@code khmer} out keeps it as it is. */
    public record PlanKhmerRequest(@Size(max = 120) String name, @Size(max = 1000) String description) {

        SubscriptionService.PlanKhmer words() {
            return new SubscriptionService.PlanKhmer(name, description);
        }
    }

    /**
     * {@code audience}: FAMILY or SCHOOL - who the plan is for (V59). As an administrator manages it ({@link #of}): the
     * English, with the Khmer in {@code khmer}; as a family or a principal reads it ({@link #forReader}): its words in
     * the reader's language, the English where no Khmer is written, and no {@code khmer}.
     */
    public record PlanSummary(UUID id, String code, String name, String description, String audience, String status,
                              boolean isDefault, boolean selfService, Price price, PlanKhmer khmer) {

        static PlanSummary of(SubscriptionPlan plan) {
            return of(plan, plan.getName(), plan.getDescription(), new PlanKhmer(plan.getNameKm(), plan.getDescriptionKm()));
        }

        static PlanSummary forReader(SubscriptionPlan plan) {
            String language = com.platform.identity.UiLanguage.ofCurrentRequest();
            return of(plan, plan.nameIn(language), plan.descriptionIn(language), null);
        }

        private static PlanSummary of(SubscriptionPlan plan, String name, String description, PlanKhmer khmer) {
            Price price = plan.getPriceAmountMinor() == null ? null
                    : new Price(plan.getPriceAmountMinor(), plan.getPriceCurrency(), plan.getBillingInterval());
            return new PlanSummary(plan.getId(), plan.getCode(), name, description, plan.getAudience().name(),
                    plan.getStatus().name(),
                    plan.isDefaultPlan(), plan.isSelfService(), price, khmer);
        }
    }

    /** {@code limit} null = unlimited; {@code used} only in a family's or a school's view. */
    public record Entitlement(EntitlementKey key, Integer limit, Integer used) {
    }

    private static List<Entitlement> entitlementList(Map<EntitlementKey, Integer> limits, Map<EntitlementKey, Integer> usage) {
        return Arrays.stream(EntitlementKey.values())
                .filter(limits::containsKey)
                .map(key -> new Entitlement(key, limits.get(key), usage == null ? null : usage.get(key)))
                .toList();
    }

    /** {@code subscribers}: the families (family plan) or schools (school plan) whose subscription names it. */
    /** A plan as an administrator manages it; {@code basedOn}: the plan it was duplicated from (V79), or null. */
    /**
     * A plan as an administrator manages it; {@code basedOn}: the plan it was duplicated from (V79), or null;
     * {@code availableFrom} / {@code availableUntil}: its offer dates (V80, null = unbounded); {@code offeredNow}: whether
     * someone new can take it up at this moment (active and inside its dates).
     */
    public record PlanResponse(PlanSummary plan, List<Entitlement> entitlements, long subscribers, PlanRef basedOn,
                               Instant availableFrom, Instant availableUntil, boolean offeredNow) {

        static PlanResponse of(SubscriptionService.PlanView view) {
            SubscriptionPlan basedOn = view.basedOn();
            SubscriptionPlan plan = view.plan();
            return new PlanResponse(PlanSummary.of(plan), entitlementList(view.limits(), null), view.subscribers(),
                    basedOn == null ? null : new PlanRef(basedOn.getId(), basedOn.getCode(), basedOn.getName()),
                    plan.getAvailableFrom(), plan.getAvailableUntil(), plan.offeredAt(Instant.now()));
        }
    }

    /** Offer dates; either may be null (no bound on that side). */
    public record AvailabilityRequest(Instant availableFrom, Instant availableUntil) {
    }

    public record PlanRef(UUID id, String code, String name) {
    }

    public record DuplicatePlanRequest(@NotBlank @Size(max = 60) String code, @NotBlank @Size(max = 120) String name) {
    }

    /** An operator's view of a subscription; {@code id} is its audit-log resource. */
    public record SubscriptionState(UUID id, String status, String source, Instant startedAt, Instant currentPeriodEnd,
                                    boolean cancelAtPeriodEnd, String note, boolean grantsAccess) {

        static SubscriptionState of(FamilySubscription subscription, boolean grantsAccess) {
            return new SubscriptionState(subscription.getId(), subscription.getStatus().name(), subscription.getSource().name(),
                    subscription.getStartedAt(), subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(),
                    subscription.getNote(), grantsAccess);
        }

        static SubscriptionState of(SchoolSubscription subscription, boolean grantsAccess) {
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

        static OwnSubscriptionState of(SchoolSubscription subscription, boolean grantsAccess) {
            return new OwnSubscriptionState(subscription.getStatus().name(), subscription.getSource().name(),
                    subscription.getStartedAt(), subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(), grantsAccess);
        }
    }

    public record PlanOfferResponse(PlanSummary plan, List<Entitlement> entitlements) {

        static PlanOfferResponse of(SubscriptionService.PlanOffer offer) {
            return new PlanOfferResponse(PlanSummary.forReader(offer.plan()), entitlementList(offer.limits(), null));
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
            return new OwnFamilyPlanResponse(PlanSummary.forReader(effective.plan()), !effective.fromSubscription(),
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

    /** {@code audience} defaults to FAMILY; it can't change once the plan exists. */
    public record CreatePlanRequest(@NotBlank @Size(max = 60) String code, @NotBlank @Size(max = 120) String name,
                                    @Size(max = 1000) String description, PlanAudience audience,
                                    @jakarta.validation.Valid PlanKhmerRequest khmer) {
    }

    /** A school's plan as its principal sees it: no operator note. */
    /** {@code access} (Y44): whether school services are paid for now, until when, and whether a payment waits. */
    public record SchoolAccess(boolean paid, Instant paidUntil, boolean paymentPending) {

        static SchoolAccess of(SchoolEntitlementService.SchoolAccess access) {
            return new SchoolAccess(access.paid(), access.paidUntil(), access.paymentPending());
        }
    }

    public record OwnSchoolPlanResponse(UUID schoolId, String schoolName, PlanSummary plan, boolean onDefaultPlan,
                                        OwnSubscriptionState subscription, List<Entitlement> entitlements, SchoolAccess access) {

        public static OwnSchoolPlanResponse of(SubscriptionService.SchoolView view) {
            SchoolEntitlementService.SchoolPlan effective = view.effective();
            return new OwnSchoolPlanResponse(view.schoolId(), view.schoolName(), PlanSummary.forReader(effective.plan()),
                    !effective.fromSubscription(),
                    effective.subscription().map(s -> OwnSubscriptionState.of(s, effective.fromSubscription())).orElse(null),
                    entitlementList(effective.limits(), view.usage()), SchoolAccess.of(view.access()));
        }
    }

    /** A school's plan as an operator sees it. */
    public record SchoolPlanResponse(UUID schoolId, String schoolName, PlanSummary plan, boolean onDefaultPlan,
                                     SubscriptionState subscription, List<Entitlement> entitlements, SchoolAccess access) {

        public static SchoolPlanResponse of(SubscriptionService.SchoolView view) {
            SchoolEntitlementService.SchoolPlan effective = view.effective();
            return new SchoolPlanResponse(view.schoolId(), view.schoolName(), PlanSummary.of(effective.plan()),
                    !effective.fromSubscription(),
                    effective.subscription().map(s -> SubscriptionState.of(s, effective.fromSubscription())).orElse(null),
                    entitlementList(effective.limits(), view.usage()), SchoolAccess.of(view.access()));
        }
    }

    /** {@code limits}: every key to set; a null value is unlimited. */
    public record RevisePlanRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
                                    boolean selfService, Long priceAmountMinor, @Size(min = 3, max = 3) String priceCurrency,
                                    BillingInterval billingInterval, @NotNull Map<EntitlementKey, Integer> limits,
                                    @jakarta.validation.Valid PlanKhmerRequest khmer) {
    }

    public record GrantRequest(@NotNull UUID planId, Instant periodEnd, @Size(max = 500) String note) {
    }

    /** {@code periodEnd} null = open-ended. */
    public record PeriodRequest(Instant periodEnd, @NotBlank @Size(max = 500) String reason) {
    }

    public record EndRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
