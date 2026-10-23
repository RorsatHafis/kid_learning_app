package com.platform.subscription.service;

import com.platform.subscription.entity.BillingInterval;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.PlanEntitlement;
import com.platform.subscription.entity.PlanStatus;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.PlanEntitlementRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M6: which plans a family's refusal names - only ones a family may choose (active, self-service, a family plan), that
 * include more than now (or no limit), the smallest steps up first, at most three; never a school's plan.
 */
class PlanOffersTests {

    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final PlanEntitlementRepository entitlements = mock(PlanEntitlementRepository.class);
    private final PlanOffers offers = new PlanOffers(plans, entitlements);
    private final List<SubscriptionPlan> catalogue = new ArrayList<>();
    private final List<PlanEntitlement> limits = new ArrayList<>();

    @Test
    void namesOnlyChoosableFamilyPlansThatIncludeMoreSmallestStepFirstAtMostThree() {
        UUID five = plan("Five", PlanAudience.FAMILY, PlanStatus.ACTIVE, true, 5, 900L);
        UUID three = plan("Three", PlanAudience.FAMILY, PlanStatus.ACTIVE, true, 3, 500L);
        UUID open = plan("Open", PlanAudience.FAMILY, PlanStatus.ACTIVE, true, null, null);
        UUID threeCheaper = plan("Three, cheaper", PlanAudience.FAMILY, PlanStatus.ACTIVE, true, 3, 400L);
        plan("Same as now", PlanAudience.FAMILY, PlanStatus.ACTIVE, true, 1, 100L);
        plan("Arranged", PlanAudience.FAMILY, PlanStatus.ACTIVE, false, 10, 100L);
        plan("Retired", PlanAudience.FAMILY, PlanStatus.RETIRED, true, 10, 100L);
        plan("School", PlanAudience.SCHOOL, PlanStatus.ACTIVE, true, 50, 100L);
        stub();

        List<PlanOffers.Offer> named = offers.unlocking(EntitlementKey.CHILDREN_MAX, 1);

        assertThat(named).extracting(PlanOffers.Offer::planId).containsExactly(threeCheaper, three, five);
        assertThat(named.getFirst().priceAmountMinor()).isEqualTo(400L);
        assertThat(named.getFirst().billingInterval()).isEqualTo("MONTH");
        assertThat(offers.unlocking(EntitlementKey.CHILDREN_MAX, 5)).extracting(PlanOffers.Offer::planId).containsExactly(open);
    }

    @Test
    void aSchoolsLimitNamesNoPlanAndNoPlanMeansNoOffer() {
        plan("School", PlanAudience.SCHOOL, PlanStatus.ACTIVE, true, 50, 100L);
        stub();
        assertThat(offers.unlocking(EntitlementKey.SCHOOL_CLASSES_MAX, 1)).isEmpty();
        assertThat(offers.unlocking(EntitlementKey.CHILDREN_MAX, 1)).isEmpty();
    }

    private UUID plan(String name, PlanAudience audience, PlanStatus status, boolean selfService, Integer children, Long price) {
        UUID id = UUID.randomUUID();
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        when(plan.getId()).thenReturn(id);
        when(plan.getName()).thenReturn(name);
        when(plan.getAudience()).thenReturn(audience);
        when(plan.getStatus()).thenReturn(status);
        when(plan.isSelfService()).thenReturn(selfService);
        when(plan.getPriceAmountMinor()).thenReturn(price);
        when(plan.getPriceCurrency()).thenReturn(price == null ? null : "USD");
        when(plan.getBillingInterval()).thenReturn(price == null ? null : BillingInterval.MONTH);
        catalogue.add(plan);
        EntitlementKey key = audience == PlanAudience.SCHOOL ? EntitlementKey.SCHOOL_CHILDREN_MAX : EntitlementKey.CHILDREN_MAX;
        limits.add(PlanEntitlement.of(id, key, children));
        return id;
    }

    private void stub() {
        when(plans.findAllByOrderByCreatedAtAsc()).thenReturn(catalogue);
        when(entitlements.findByPlanIdIn(anyList())).thenReturn(limits);
    }

}
