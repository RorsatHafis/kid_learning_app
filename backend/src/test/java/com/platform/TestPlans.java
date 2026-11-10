package com.platform;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Since V88 (owner 2026-11-10) a family without a subscription is on the free plan: one child, three subjects, one
 * learned at a time. Tests of other features whose families need more (a second child, a subject beside one the child
 * was started on automatically) give that family "Early access" - the plan every family had before V88, everything
 * included - as an administrator would grant it. The free plan's own limits are tested in FreePlanIntegrationTests.
 */
public final class TestPlans {

    /** V42's "Early access": no limits (V88 kept it active, no longer the default). */
    public static final UUID EVERYTHING_INCLUDED = UUID.fromString("6f1c2a60-0000-4000-8000-000000000001");

    private TestPlans() {
    }

    /** Grants "Early access" to the family a registration answer ({@code POST /api/v1/auth/register}) created; returns the answer. */
    public static tools.jackson.databind.JsonNode everythingIncluded(JdbcTemplate jdbc, tools.jackson.databind.JsonNode registered) {
        everythingIncluded(jdbc, registered.get("email").asString());
        return registered;
    }

    /** Grants "Early access" to the family {@code parentEmail} owns (open-ended, like an administrator's grant). */
    public static void everythingIncluded(JdbcTemplate jdbc, String parentEmail) {
        UUID familyId = jdbc.queryForObject("""
                select m.family_id from memberships m join accounts a on a.id = m.account_id
                where a.normalized_email = lower(?) and m.role = 'OWNER'
                """, UUID.class, parentEmail.strip());
        jdbc.update("""
                insert into family_subscriptions (id, family_id, plan_id, status, source, started_at, note)
                values (?, ?, ?, 'ACTIVE', 'ADMIN_GRANT', now(), 'Test: everything included')
                on conflict (family_id) do update set plan_id = excluded.plan_id, status = 'ACTIVE', current_period_end = null
                """, UUID.randomUUID(), familyId, EVERYTHING_INCLUDED);
    }
}
