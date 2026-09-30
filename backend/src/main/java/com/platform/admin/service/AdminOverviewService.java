package com.platform.admin.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * The administrator's home (X7): what is waiting on an administrator, and the platform in counts.
 * Counts only, apart from the families whose plan ends soon (an administrator acts on those).
 * Never a child's name or learning: that is the family's, and a class's staff's within PD-01.
 */
@Service
public class AdminOverviewService {

    /** How far ahead a plan's end counts as "soon" (the family's own view uses the same two weeks). */
    static final Duration ENDING_SOON = Duration.ofDays(14);
    private static final int ENDING_SOON_SHOWN = 20;

    private final AccountRepository accounts;
    private final NamedParameterJdbcTemplate jdbc;

    public AdminOverviewService(AccountRepository accounts, NamedParameterJdbcTemplate jdbc) {
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    public record EndingPlan(UUID familyId, String familyName, String parentEmail, String planName, Instant endsAt) {
    }

    /** @param plansEndingSoon the soonest first, at most 20; {@code plansEndingSoonTotal} counts them all */
    public record Waiting(long principalsAwaitingApproval, long suspendedAccounts, long lockoutsLast24Hours,
                          List<EndingPlan> plansEndingSoon, long plansEndingSoonTotal) {
    }

    public record Platform(long families, long children, long schools, long classes, long teachers, long principals) {
    }

    /** Items (not versions) with an open draft, and curricula with a published version. */
    public record Content(long questionsInDraft, long activitiesInDraft, long curriculaInDraft, long curriculaPublished) {
    }

    public record AdminOverview(Waiting waiting, Platform platform, Content content) {
    }

    @Transactional(readOnly = true)
    public AdminOverview overview(UUID adminId) {
        requireAdmin(adminId);
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("soon", Timestamp.from(now.plus(ENDING_SOON)))
                .addValue("dayAgo", Timestamp.from(now.minus(Duration.ofHours(24))));

        // A plan in force whose end falls within the next two weeks (a lapsed one no longer grants access).
        String endingSoon = """
                s.status in ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED')
                and s.current_period_end > :now and s.current_period_end <= :soon
                """;
        List<EndingPlan> ending = jdbc.query("""
                select s.family_id, f.name as family_name, p.name as plan_name, s.current_period_end,
                       (select a.email from memberships m join accounts a on a.id = m.account_id
                        where m.family_id = s.family_id and m.status = 'ACTIVE'
                        order by m.created_at, m.id limit 1) as parent_email
                from family_subscriptions s
                join families f on f.id = s.family_id
                join subscription_plans p on p.id = s.plan_id
                where
                """ + endingSoon + """
                order by s.current_period_end, s.family_id
                limit :shown
                """, new MapSqlParameterSource(params.getValues()).addValue("shown", ENDING_SOON_SHOWN),
                (rs, i) -> new EndingPlan(rs.getObject("family_id", UUID.class), rs.getString("family_name"),
                        rs.getString("parent_email"), rs.getString("plan_name"),
                        rs.getTimestamp("current_period_end").toInstant()));

        Waiting waiting = new Waiting(
                count("select count(*) from accounts where platform_role = 'PRINCIPAL' and status = 'PENDING_VERIFICATION'", params),
                count("select count(*) from accounts where status = 'DISABLED'", params),
                count("select count(*) from audit_events where action = 'ACCOUNT_LOCKED' and occurred_at > :dayAgo", params),
                ending,
                count("select count(*) from family_subscriptions s where " + endingSoon, params));
        Platform platform = new Platform(
                count("select count(*) from families", params),
                count("select count(*) from children where status = 'ACTIVE'", params),
                count("select count(*) from schools", params),
                count("select count(*) from school_classes", params),
                count("select count(*) from accounts where platform_role = 'TEACHER' and status = 'ACTIVE'", params),
                count("select count(*) from accounts where platform_role = 'PRINCIPAL' and status = 'ACTIVE'", params));
        Content content = new Content(
                count("select count(distinct question_id) from question_versions where status = 'DRAFT'", params),
                count("select count(distinct activity_id) from activity_versions where status = 'DRAFT'", params),
                count("select count(distinct curriculum_id) from curriculum_versions where status = 'DRAFT'", params),
                count("select count(distinct curriculum_id) from curriculum_versions where status = 'PUBLISHED'", params));
        return new AdminOverview(waiting, platform, content);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private void requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
    }
}
