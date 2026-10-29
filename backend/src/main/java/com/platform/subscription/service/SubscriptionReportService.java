package com.platform.subscription.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.repository.FamilyRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.School;
import com.platform.school.repository.SchoolRepository;
import com.platform.subscription.entity.FamilySubscription;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.PlanStatus;
import com.platform.subscription.entity.SchoolSubscription;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.entity.SubscriptionStatus;
import com.platform.subscription.repository.FamilySubscriptionRepository;
import com.platform.subscription.repository.SchoolSubscriptionRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is on which plan right now, for a platform administrator (flexible plan management, slice 1). Families are
 * counted, never listed: an operator reaches one family by a parent's exact address only. Schools are listed when
 * their paid period is about to end, because that is what an administrator acts on (an invoice for the renewal).
 * <p>
 * Standing is read with the same rules that grant access - {@link FamilySubscription#grantsAccessAt} for a family,
 * a confirmed payment ({@link SchoolEntitlementService#access}) for a school - so the report can't call a customer
 * subscribed while the product treats them as not.
 */
@Service
public class SubscriptionReportService {

    /** How far ahead "ends soon" looks. */
    public static final Duration ENDING_WINDOW = Duration.ofDays(30);

    private final SubscriptionPlanRepository plans;
    private final FamilySubscriptionRepository familySubscriptions;
    private final SchoolSubscriptionRepository schoolSubscriptions;
    private final SchoolEntitlementService schoolEntitlements;
    private final FamilyRepository families;
    private final SchoolRepository schools;
    private final AccountRepository accounts;

    public SubscriptionReportService(SubscriptionPlanRepository plans, FamilySubscriptionRepository familySubscriptions,
                                     SchoolSubscriptionRepository schoolSubscriptions, SchoolEntitlementService schoolEntitlements,
                                     FamilyRepository families, SchoolRepository schools, AccountRepository accounts) {
        this.plans = plans;
        this.familySubscriptions = familySubscriptions;
        this.schoolSubscriptions = schoolSubscriptions;
        this.schoolEntitlements = schoolEntitlements;
        this.families = families;
        this.schools = schools;
        this.accounts = accounts;
    }

    /**
     * Subscriptions naming one plan, by standing now. {@code endingSoon} counts those in force whose period ends
     * within {@link #ENDING_WINDOW} (whatever their standing); {@code ended} counts rows no longer in force (for a
     * school: ended, refunded or never paid).
     */
    public record PlanCounts(UUID planId, String code, String name, PlanAudience audience, PlanStatus status, boolean isDefault,
                             long active, long trialing, long pastDue, long cancelling, long endingSoon, long ended) {
    }

    public record SchoolEnding(UUID schoolId, String schoolName, String planName, Instant paidUntil) {
    }

    /**
     * @param familiesOnDefault     families without a subscription in force (the families' default plan applies)
     * @param schoolsWithoutPaidPlan schools in use (not archived) without a paid plan in force: their services wait
     */
    public record Summary(Instant at, int endingWithinDays, List<PlanCounts> plans, long familiesOnDefault,
                          long schoolsWithoutPaidPlan, List<SchoolEnding> schoolsEndingSoon) {
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID adminId) {
        requireAdmin(adminId);
        Instant now = Instant.now();
        Instant soon = now.plus(ENDING_WINDOW);
        Map<UUID, long[]> counts = new HashMap<>();

        long familiesInForce = 0;
        for (FamilySubscription s : familySubscriptions.findAll()) {
            boolean inForce = s.grantsAccessAt(now);
            familiesInForce += inForce ? 1 : 0;
            tally(counts, s.getPlanId(), inForce, s.getStatus(), s.isCancelAtPeriodEnd(), s.getCurrentPeriodEnd(), soon);
        }

        Map<UUID, School> schoolsById = new HashMap<>();
        schools.findAll().forEach(school -> schoolsById.put(school.getId(), school));
        Map<UUID, String> planNames = new HashMap<>();
        List<SubscriptionPlan> all = plans.findAllByOrderByCreatedAtAsc();
        all.forEach(plan -> planNames.put(plan.getId(), plan.getName()));
        List<SchoolEnding> ending = new ArrayList<>();
        long schoolsPaid = 0;
        for (SchoolSubscription s : schoolSubscriptions.findAll()) {
            School school = schoolsById.get(s.getSchoolId());
            SchoolEntitlementService.SchoolAccess access = schoolEntitlements.access(s.getSchoolId());
            boolean inForce = access.paid();
            if (inForce && school != null && !school.isArchived()) {
                schoolsPaid++;
            }
            tally(counts, s.getPlanId(), inForce, s.getStatus(), s.isCancelAtPeriodEnd(), access.paidUntil(), soon);
            if (inForce && school != null && access.paidUntil() != null && access.paidUntil().isBefore(soon)) {
                ending.add(new SchoolEnding(school.getId(), school.getName(), planNames.getOrDefault(s.getPlanId(), "?"), access.paidUntil()));
            }
        }
        long schoolsInUse = schoolsById.values().stream().filter(school -> !school.isArchived()).count();
        ending.sort(Comparator.comparing(SchoolEnding::paidUntil));

        List<PlanCounts> rows = all.stream().map(plan -> {
            long[] c = counts.getOrDefault(plan.getId(), new long[6]);
            return new PlanCounts(plan.getId(), plan.getCode(), plan.getName(), plan.getAudience(), plan.getStatus(), plan.isDefaultPlan(),
                    c[0], c[1], c[2], c[3], c[4], c[5]);
        }).toList();
        return new Summary(now, (int) ENDING_WINDOW.toDays(), rows, Math.max(0, families.count() - familiesInForce),
                Math.max(0, schoolsInUse - schoolsPaid), ending);
    }

    /** Index: 0 active, 1 trialing, 2 past due, 3 cancelling, 4 ending soon, 5 ended. */
    private static void tally(Map<UUID, long[]> counts, UUID planId, boolean inForce, SubscriptionStatus status, boolean cancelAtPeriodEnd,
                              Instant periodEnd, Instant soon) {
        long[] c = counts.computeIfAbsent(planId, id -> new long[6]);
        if (!inForce) {
            c[5]++;
            return;
        }
        if (status == SubscriptionStatus.TRIALING) {
            c[1]++;
        } else if (status == SubscriptionStatus.PAST_DUE) {
            c[2]++;
        } else if (status == SubscriptionStatus.CANCELED || cancelAtPeriodEnd) {
            c[3]++;
        } else {
            c[0]++;
        }
        if (periodEnd != null && periodEnd.isBefore(soon)) {
            c[4]++;
        }
    }

    private void requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
    }
}
