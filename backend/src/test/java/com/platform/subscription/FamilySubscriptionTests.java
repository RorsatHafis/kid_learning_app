package com.platform.subscription;

import com.platform.subscription.entity.FamilySubscription;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.entity.BillingInterval;
import com.platform.subscription.entity.SubscriptionSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    void aGrantInForceCanBeExtendedShortenedOrOpenedUpButNotRevived() {
        FamilySubscription grant = FamilySubscription.grant(UUID.randomUUID(), UUID.randomUUID(), NOW, NOW.plus(Duration.ofDays(30)), null);

        grant.changePeriodEnd(NOW.plus(Duration.ofDays(90)), NOW.plus(Duration.ofDays(1)));
        assertThat(grant.grantsAccessAt(NOW.plus(Duration.ofDays(60)))).isTrue();
        assertThat(grant.getStartedAt()).isEqualTo(NOW);

        grant.changePeriodEnd(null, NOW.plus(Duration.ofDays(2)));
        assertThat(grant.getCurrentPeriodEnd()).isNull();

        // The new end is in the future; stopping now is "end", not a date change.
        assertThatThrownBy(() -> grant.changePeriodEnd(NOW, NOW.plus(Duration.ofDays(3)))).isInstanceOf(IllegalArgumentException.class);

        FamilySubscription lapsed = FamilySubscription.grant(UUID.randomUUID(), UUID.randomUUID(), NOW, NOW.plus(Duration.ofDays(1)), null);
        assertThatThrownBy(() -> lapsed.changePeriodEnd(NOW.plus(Duration.ofDays(30)), NOW.plus(Duration.ofDays(2))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aProvidersSubscriptionIsNotChangedByHand() {
        FamilySubscription paid = FamilySubscription.grant(UUID.randomUUID(), UUID.randomUUID(), NOW, NOW.plus(Duration.ofDays(30)), null);
        ReflectionTestUtils.setField(paid, "source", SubscriptionSource.PROVIDER);

        assertThatThrownBy(() -> paid.changePeriodEnd(NOW.plus(Duration.ofDays(60)), NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> paid.end(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> paid.regrant(UUID.randomUUID(), NOW, null, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPriceIsAllOfItsPartsOrNone() {
        SubscriptionPlan plan = SubscriptionPlan.draft("FAMILY", "Family", null);
        assertThatThrownBy(() -> plan.revise("Family", null, true, 500L, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.revise("Family", null, true, 500L, "XYZ", BillingInterval.MONTH))
                .isInstanceOf(IllegalArgumentException.class);
        plan.revise("Family", null, true, 500L, "usd", BillingInterval.MONTH);
        assertThat(plan.getPriceCurrency()).isEqualTo("USD");

        plan.activate();
        assertThatThrownBy(() -> plan.revise("Family", null, true, 600L, "USD", BillingInterval.MONTH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(plan::activate).isInstanceOf(IllegalStateException.class);
    }
}
