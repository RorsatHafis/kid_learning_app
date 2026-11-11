package com.platform.subscription.choice;

import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner requirement 2026-11-11: a visitor chooses a plan before registering (V92). What can be chosen is decided here,
 * from the plan records only: the family default plan (free - nothing to pay, it applies by itself; a school has no free
 * plan, its services open only once a payment is confirmed, Y44) or a priced plan
 * people may choose themselves, active and inside its offer dates (the plans the public pages show). The browser names
 * a plan by its code; every price, limit and audience is read from the record.
 *
 * <p>A paid choice is kept on the new account until it is paid for - the plan starts only from a confirmed payment
 * ({@code PaymentService}), which also clears the choice - or set aside for the free plan. A choice grants nothing.
 */
@Service
public class PlanChoices {

    private final SubscriptionPlanRepository plans;
    private final JdbcTemplate jdbc;

    public PlanChoices(SubscriptionPlanRepository plans, JdbcTemplate jdbc) {
        this.plans = plans;
        this.jdbc = jdbc;
    }

    /** A chosen plan as the record holds it now, in the reader's language; {@code offered}: still on offer today. */
    public record ChosenPlan(UUID id, String code, String audience, String name, String description, boolean isDefault,
                             Long amountMinor, String currency, String billingInterval, boolean offered) {
    }

    /** The plan a visitor may choose by this code now, or a refusal (400) naming no internal detail. */
    @Transactional(readOnly = true)
    public SubscriptionPlan requireChooseable(String code) {
        return plans.findByCode(code == null ? "" : code.strip())
                .filter(plan -> chooseable(plan, Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("That plan isn't offered now. Choose another plan."));
    }

    /** The plan as the reader sees it (null id: none). */
    @Transactional(readOnly = true)
    public Optional<ChosenPlan> describe(UUID planId, String language) {
        if (planId == null) return Optional.empty();
        Instant now = Instant.now();
        return plans.findById(planId).map(plan -> new ChosenPlan(plan.getId(), plan.getCode(), plan.getAudience().name(),
                plan.nameIn(language), plan.descriptionIn(language), plan.isDefaultPlan(), plan.getPriceAmountMinor(),
                plan.getPriceCurrency(), plan.getBillingInterval() == null ? null : plan.getBillingInterval().name(),
                chooseable(plan, now)));
    }

    /** Whether the plan is the one the account's registration must be for (a school plan: a school's principal). */
    public PlanAudience audienceOf(UUID planId) {
        return plans.findById(planId).map(SubscriptionPlan::getAudience)
                .orElseThrow(() -> new IllegalArgumentException("That plan isn't offered now. Choose another plan."));
    }

    /**
     * At registration, in its transaction: a paid choice is kept on the new account; the free (default) plan needs
     * nothing kept - it already applies.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void keepForNewAccount(UUID accountId, UUID planId) {
        SubscriptionPlan plan = plans.findById(planId).orElseThrow();
        if (plan.isDefaultPlan()) return;
        jdbc.update("""
                insert into plan_choices (account_id, plan_id, chosen_at) values (?, ?, ?)
                on conflict (account_id) do update set plan_id = excluded.plan_id, chosen_at = excluded.chosen_at
                """, accountId, planId, java.sql.Timestamp.from(Instant.now()));
    }

    /** The account's chosen plan still waiting for its payment, if any. */
    @Transactional(readOnly = true)
    public Optional<ChosenPlan> current(UUID accountId, String language) {
        return jdbc.queryForList("select plan_id from plan_choices where account_id = ?", UUID.class, accountId).stream()
                .findFirst().flatMap(planId -> describe(planId, language));
    }

    /** The person keeps the free plan instead (or chooses again later from the plan page). */
    @Transactional
    public void setAside(UUID accountId) {
        jdbc.update("delete from plan_choices where account_id = ?", accountId);
    }

    /** A confirmed payment for the plan settles the choice of everyone in the family it was paid for. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void settledForFamily(UUID familyId, UUID planId) {
        jdbc.update("""
                delete from plan_choices where plan_id = ?
                  and account_id in (select account_id from memberships where family_id = ?)
                """, planId, familyId);
    }

    /** ... and of the school's principal. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void settledForSchool(UUID schoolId, UUID planId) {
        jdbc.update("""
                delete from plan_choices where plan_id = ?
                  and account_id in (select principal_account_id from schools where id = ?)
                """, planId, schoolId);
    }

    /** The one rule for what can be chosen before registering (the public plans page shows it too). */
    public static boolean chooseable(SubscriptionPlan plan, Instant now) {
        if (!plan.offeredAt(now)) return false;
        // Free: the family default only - a school opens only once a payment is confirmed (Y44).
        if (plan.isDefaultPlan()) return plan.getAudience() == PlanAudience.FAMILY;
        return (plan.isSelfService() && plan.getPriceAmountMinor() != null
                && plan.getPriceCurrency() != null && plan.getBillingInterval() != null);
    }
}
