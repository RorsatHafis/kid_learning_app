package com.platform.subscription;

import com.platform.subscription.entity.FamilySubscription;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.entity.BillingInterval;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** When a subscription grants its plan, and the rules a plan keeps. */
class FamilySubscriptionTests {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");

    @Test
    void aGrantGivesItsPlanUntilItsPeriodEndsThenNoLonger() {
        FamilySubscription grant = FamilySubscription.grant(UUID.randomUUID(), UUID.randomUUID(), NOW, NOW.plus(Duration.ofDays(30)), null);

        assertThat(grant.grantsAccessAt(NOW.plus(Duration.ofDays(29)))).isTrue();
        assertThat(grant.grantsAccessAt(NOW.plus(Duration.ofDays(30)))).isFalse();
    }

    @Test
    void anOpenEndedGrantLastsUntilEnded() {
        FamilySubscription grant = FamilySubscription.grant(UUID.randomUUID(), UUID.randomUUID(), NOW, null, "School partner");
        assertThat(grant.grantsAccessAt(NOW.plus(Duration.ofDays(3650)))).isTrue();

        grant.end(NOW.plus(Duration.ofDays(1)));
        assertThat(grant.grantsAccessAt(NOW.plus(Duration.ofDays(2)))).isFalse();
        assertThatThrownBy(() -> grant.end(NOW.plus(Duration.ofDays(3)))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aGrantCantEndInThePast() {
        assertThatThrownBy(() -> FamilySubscription.grant(UUID.randomUUID(), UUID.randomUUID(), NOW, NOW.minusSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPriceIsAllOfItsPartsOrNone() {
        SubscriptionPlan plan = SubscriptionPlan.draft("FAMILY", "Family", null);
        assertThatThrownBy(() -> plan.revise("Family", null, true, 500L, null, null)).isInstanceOf(IllegalArgumentException.class);
        plan.revise("Family", null, true, 500L, "usd", BillingInterval.MONTH);
        assertThat(plan.getPriceCurrency()).isEqualTo("USD");

        plan.activate();
        assertThatThrownBy(() -> plan.revise("Family", null, true, 600L, "USD", BillingInterval.MONTH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(plan::activate).isInstanceOf(IllegalStateException.class);
    }
}
