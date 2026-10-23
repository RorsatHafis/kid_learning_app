package com.platform.subscription.service;

import com.platform.school.repository.SchoolRepository;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.SchoolSubscription;
import com.platform.subscription.repository.SchoolSubscriptionRepository;
import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.Family;
import com.platform.family.entity.Membership;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.FamilyRepository;
import com.platform.family.repository.MembershipRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.subscription.entity.BillingInterval;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.FamilySubscription;
import com.platform.subscription.entity.PlanEntitlement;
import com.platform.subscription.entity.PlanStatus;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.FamilySubscriptionRepository;
import com.platform.subscription.repository.PlanEntitlementRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Plans and family subscriptions, run by an administrator (CLAUDE.md §22: explicit and
 * audited). Every change here is what a payment provider's events will also drive once one
 * is integrated - the provider becomes another source of the same transitions, not a second
 * system. A family's own view is {@link #familyPlan}.
 */
@Service
public class SubscriptionService {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,60}");
    private static final String RESOURCE = "FAMILY_SUBSCRIPTION";

    private final SubscriptionPlanRepository plans;
    private final PlanEntitlementRepository entitlements;
    private final FamilySubscriptionRepository subscriptions;
    private final FamilyRepository families;
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final ChildRepository children;
    private final EntitlementService entitlementService;
    private final AuditService audit;
    private final PlanRequestService planRequests;
    private final SchoolSubscriptionRepository schoolSubscriptions;
    private final SchoolEntitlementService schoolEntitlements;
    private final SchoolRepository schools;

    public SubscriptionService(SubscriptionPlanRepository plans, PlanEntitlementRepository entitlements,
                               FamilySubscriptionRepository subscriptions, FamilyRepository families,
                               MembershipRepository memberships, AccountRepository accounts, ChildRepository children,
                               EntitlementService entitlementService, AuditService audit,
                               PlanRequestService planRequests, SchoolSubscriptionRepository schoolSubscriptions,
                               SchoolEntitlementService schoolEntitlements, SchoolRepository schools) {
        this.schoolSubscriptions = schoolSubscriptions;
        this.schoolEntitlements = schoolEntitlements;
        this.schools = schools;
        this.plans = plans;
        this.entitlements = entitlements;
        this.subscriptions = subscriptions;
        this.families = families;
        this.memberships = memberships;
        this.accounts = accounts;
        this.children = children;
        this.entitlementService = entitlementService;
        this.audit = audit;
        this.planRequests = planRequests;
    }

    // ------------------------------------------------------------------ views

    /** {@code subscribers}: families (family plan) or schools (school plan) whose subscription names it. */
    public record PlanView(SubscriptionPlan plan, Map<EntitlementKey, Integer> limits, long subscribers) {
    }

    /** A school as an operator or its principal sees it: its plan and what it holds. */
    /** {@code access}: whether school services are paid for now (Y44). */
    public record SchoolView(UUID schoolId, String schoolName, SchoolEntitlementService.SchoolPlan effective,
                             Map<EntitlementKey, Integer> usage, SchoolEntitlementService.SchoolAccess access) {
    }

    public record ParentRef(UUID accountId, String email) {
    }

    /** A family as an operator sees it: who, how many children, what it has and uses. */
    public record FamilyView(UUID familyId, String familyName, List<ParentRef> parents, int childCount,
                             EntitlementService.FamilyPlan effective, Map<EntitlementKey, Integer> usage) {
    }

    /** A plan as a family may compare it: what it includes. */
    public record PlanOffer(SubscriptionPlan plan, Map<EntitlementKey, Integer> limits) {
    }

    /**
     * A family's own view of its plan.
     *
     * @param afterwards what applies once its subscription ends - the default plan - when an end is set
     * @param choices    other active plans marked for families to choose themselves (none today; choosing
     *                   one needs a payment provider, PD-09)
     */
    public record FamilyPlanView(EntitlementService.FamilyPlan effective, Map<EntitlementKey, Integer> usage,
                                 Optional<PlanOffer> afterwards, List<PlanOffer> choices,
                                 Optional<PlanRequestService.OpenRequest> request) {
    }

    // ------------------------------------------------------------------ plans

    @Transactional(readOnly = true)
    public List<PlanView> listPlans(UUID adminId) {
        requireAdmin(adminId);
        return plans.findAllByOrderByCreatedAtAsc().stream().map(this::view).toList();
    }

    /** A new plan starts as a draft with its audience's default plan's limits, so nothing is left unset by accident. */
    @Transactional
    public PlanView createPlan(UUID adminId, String code, String name, String description) {
        return createPlan(adminId, code, name, description, PlanAudience.FAMILY);
    }

    @Transactional
    public PlanView createPlan(UUID adminId, String code, String name, String description, PlanAudience audience) {
        requireAdmin(adminId);
        String normalized = code == null ? "" : code.strip().toUpperCase();
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("A plan code uses capital letters, numbers and _ (2–60 characters)");
        }
        if (plans.findByCode(normalized).isPresent()) {
            throw new IllegalStateException("A plan with the code " + normalized + " already exists");
        }
        SubscriptionPlan plan = plans.save(SubscriptionPlan.draft(normalized, name, description, audience));
        Map<EntitlementKey, Integer> start = entitlementService.limitsOf(entitlementService.defaultPlan(audience).getId());
        for (EntitlementKey key : EntitlementKey.of(audience)) {
            entitlements.save(PlanEntitlement.of(plan.getId(), key, start.get(key)));
        }
        record(adminId, "PLAN_CREATED", plan.getId(), Map.of("code", normalized, "audience", audience.name()));
        return view(plan);
    }

    @Transactional
    public PlanView revisePlan(UUID adminId, UUID planId, String name, String description, boolean selfService,
                               Long priceAmountMinor, String priceCurrency, BillingInterval interval,
                               Map<EntitlementKey, Integer> limits) {
        requireAdmin(adminId);
        SubscriptionPlan plan = plan(planId);
        if (selfService && plan.getAudience() == PlanAudience.SCHOOL && priceAmountMinor == null) {
            // M4: a principal may pick a self-service school plan and pay for it, so it needs a price; an unpriced
            // school plan is arranged by an administrator.
            throw new IllegalArgumentException("A school plan can be chosen by schools only once it has a price");
        }
        plan.revise(name, description, selfService, priceAmountMinor, priceCurrency, interval);
        for (Map.Entry<EntitlementKey, Integer> entry : limits.entrySet()) {
            if (entry.getKey().audience() != plan.getAudience()) {
                throw new IllegalArgumentException("This limit belongs to another kind of plan");
            }
            PlanEntitlement entitlement = entitlements.findByPlanIdAndKey(planId, entry.getKey())
                    .orElseGet(() -> entitlements.save(PlanEntitlement.of(planId, entry.getKey(), null)));
            entitlement.setLimit(entry.getValue());
        }
        record(adminId, "PLAN_REVISED", planId, Map.of("code", plan.getCode()));
        return view(plan);
    }

    @Transactional
    public PlanView activatePlan(UUID adminId, UUID planId) {
        requireAdmin(adminId);
        SubscriptionPlan plan = plan(planId);
        Map<EntitlementKey, Integer> limits = entitlementService.limitsOf(planId);
        for (EntitlementKey key : EntitlementKey.of(plan.getAudience())) {
            if (!limits.containsKey(key)) {
                throw new IllegalStateException("Set every entitlement before activating (missing " + key + ")");
            }
        }
        plan.activate();
        record(adminId, "PLAN_ACTIVATED", planId, Map.of("code", plan.getCode()));
        return view(plan);
    }

    @Transactional
    public PlanView retirePlan(UUID adminId, UUID planId) {
        requireAdmin(adminId);
        SubscriptionPlan plan = plan(planId);
        plan.retire();
        record(adminId, "PLAN_RETIRED", planId, Map.of("code", plan.getCode()));
        return view(plan);
    }

    /**
     * Families (or schools) without a subscription get their audience's default plan; changing it
     * changes what they can add from now on.
     */
    @Transactional
    public PlanView makeDefault(UUID adminId, UUID planId) {
        requireAdmin(adminId);
        SubscriptionPlan plan = plan(planId);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalStateException("Only an active plan can be the default");
        }
        Optional<SubscriptionPlan> current = plans.findDefaultForUpdate(plan.getAudience());
        if (current.isPresent() && current.get().getId().equals(planId)) {
            return view(plan);
        }
        current.ifPresent(previous -> {
            previous.clearDefault();
            plans.saveAndFlush(previous); // clear before setting: at most one default (unique index)
        });
        plan.makeDefault();
        record(adminId, "PLAN_MADE_DEFAULT", planId, Map.of("code", plan.getCode(),
                "previous", current.map(SubscriptionPlan::getCode).orElse("none")));
        return view(plan);
    }

    // ------------------------------------------------------------------ families

    /** An operator acts on a specific family, found by a parent's exact address (not by browsing families). */
    @Transactional(readOnly = true)
    public Optional<FamilyView> findFamilyByParentEmail(UUID adminId, String email) {
        requireAdmin(adminId);
        return accounts.findByNormalizedEmail(Account.normalize(email))
                .filter(account -> account.getPlatformRole() == PlatformRole.PARENT)
                .flatMap(account -> memberships.findByAccountId(account.getId()).stream()
                        .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                        .map(Membership::getFamilyId)
                        .findFirst())
                .map(this::familyView);
    }

    @Transactional
    public FamilyView grant(UUID adminId, UUID familyId, UUID planId, Instant periodEnd, String note) {
        requireAdmin(adminId);
        families.findByIdForUpdate(familyId).orElseThrow(() -> new ResourceNotFoundException("Family", familyId));
        SubscriptionPlan plan = plan(planId);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalStateException("Only an active plan can be given to a family");
        }
        if (plan.getAudience() != PlanAudience.FAMILY) {
            throw new IllegalArgumentException("This is a school plan: give it to a school");
        }
        Instant now = Instant.now();
        Optional<FamilySubscription> existing = subscriptions.findByFamilyIdForUpdate(familyId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("plan", plan.getCode());
        metadata.put("until", periodEnd == null ? "open-ended" : periodEnd.toString());
        existing.ifPresent(previous -> {
            metadata.put("previousPlan", plans.findById(previous.getPlanId()).map(SubscriptionPlan::getCode).orElse("?"));
            metadata.put("previousStatus", previous.getStatus().name());
        });
        if (note != null && !note.isBlank()) {
            metadata.put("note", note.strip());
        }
        FamilySubscription subscription = existing.orElse(null);
        if (subscription == null) {
            subscription = subscriptions.save(FamilySubscription.grant(familyId, planId, now, periodEnd, note));
        } else {
            subscription.regrant(planId, now, periodEnd, note);
        }
        record(adminId, "SUBSCRIPTION_GRANTED", subscription.getId(), metadata);
        // Y11: a family's open request for this plan is answered by the grant.
        planRequests.onGranted(adminId, familyId, planId);
        return familyView(familyId);
    }

    /** Extends, shortens or opens up a grant still in force, keeping its plan and start. */
    @Transactional
    public FamilyView changePeriodEnd(UUID adminId, UUID familyId, Instant periodEnd, String reason) {
        requireAdmin(adminId);
        FamilySubscription subscription = subscriptions.findByFamilyIdForUpdate(familyId)
                .orElseThrow(() -> new IllegalStateException("This family has no subscription to change"));
        Instant previous = subscription.getCurrentPeriodEnd();
        subscription.changePeriodEnd(periodEnd, Instant.now());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("plan", plans.findById(subscription.getPlanId()).map(SubscriptionPlan::getCode).orElse("?"));
        metadata.put("from", previous == null ? "open-ended" : previous.toString());
        metadata.put("until", periodEnd == null ? "open-ended" : periodEnd.toString());
        metadata.put("reason", reason == null ? "" : reason.strip());
        record(adminId, "SUBSCRIPTION_PERIOD_CHANGED", subscription.getId(), metadata);
        return familyView(familyId);
    }

    @Transactional
    public FamilyView end(UUID adminId, UUID familyId, String reason) {
        requireAdmin(adminId);
        FamilySubscription subscription = subscriptions.findByFamilyIdForUpdate(familyId)
                .orElseThrow(() -> new IllegalStateException("This family has no subscription to end"));
        subscription.end(Instant.now());
        record(adminId, "SUBSCRIPTION_ENDED", subscription.getId(), Map.of("reason", reason == null ? "" : reason.strip(),
                "plan", plans.findById(subscription.getPlanId()).map(SubscriptionPlan::getCode).orElse("?")));
        return familyView(familyId);
    }

    // ------------------------------------------------------------------ schools

    /** A school's plan and what it holds - for an administrator (any school) or its principal (SchoolAccessGuard). */
    @Transactional(readOnly = true)
    public SchoolView schoolView(UUID schoolId) {
        com.platform.school.entity.School school = schools.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
        return new SchoolView(schoolId, school.getName(), schoolEntitlements.effectivePlan(schoolId), schoolEntitlements.usage(schoolId),
                schoolEntitlements.access(schoolId));
    }

    @Transactional(readOnly = true)
    public SchoolView adminSchoolView(UUID adminId, UUID schoolId) {
        requireAdmin(adminId);
        return schoolView(schoolId);
    }

    /**
     * Refused since Y44: a school's plan starts only when its payment is confirmed (invoice the school, then confirm
     * the transfer against its receipt). A plan given without payment would look like school access it isn't.
     */
    @Transactional
    public SchoolView grantSchool(UUID adminId, UUID schoolId, UUID planId, Instant periodEnd, String note) {

        requireAdmin(adminId);
        throw new IllegalStateException("A school's plan starts when its payment is confirmed: invoice the school instead");

    }

    @Transactional
    public SchoolView changeSchoolPeriodEnd(UUID adminId, UUID schoolId, Instant periodEnd, String reason) {
        requireAdmin(adminId);
        SchoolSubscription subscription = schoolSubscriptions.findBySchoolIdForUpdate(schoolId)
                .orElseThrow(() -> new IllegalStateException("This school has no subscription to change"));
        Instant previous = subscription.getCurrentPeriodEnd();
        subscription.changePeriodEnd(periodEnd, Instant.now());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("plan", plans.findById(subscription.getPlanId()).map(SubscriptionPlan::getCode).orElse("?"));
        metadata.put("from", previous == null ? "open-ended" : previous.toString());
        metadata.put("until", periodEnd == null ? "open-ended" : periodEnd.toString());
        metadata.put("reason", reason == null ? "" : reason.strip());
        recordSchool(adminId, "SCHOOL_SUBSCRIPTION_PERIOD_CHANGED", subscription.getId(), metadata);
        return schoolView(schoolId);
    }

    /** Ends it now; the school's default plan applies from now on. Nothing the school holds is removed. */
    @Transactional
    public SchoolView endSchool(UUID adminId, UUID schoolId, String reason) {
        requireAdmin(adminId);
        SchoolSubscription subscription = schoolSubscriptions.findBySchoolIdForUpdate(schoolId)
                .orElseThrow(() -> new IllegalStateException("This school has no subscription to end"));
        subscription.end(Instant.now());
        recordSchool(adminId, "SCHOOL_SUBSCRIPTION_ENDED", subscription.getId(), Map.of("reason", reason == null ? "" : reason.strip(),
                "plan", plans.findById(subscription.getPlanId()).map(SubscriptionPlan::getCode).orElse("?")));
        return schoolView(schoolId);
    }

    // ------------------------------------------------------------------ the family's own view

    /** The signed-in parent's family plan: what it includes and what the family uses. */
    @Transactional(readOnly = true)
    public FamilyPlanView familyPlan(UUID accountId) {
        return familyPlanOf(accountId).orElseThrow(() -> new ResourceNotFoundException("Family for account", accountId));
    }

    /** The same, or empty for an account that is in no family (e.g. every membership ended). */
    @Transactional(readOnly = true)
    public Optional<FamilyPlanView> familyPlanOf(UUID accountId) {
        return memberships.findByAccountId(accountId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(Membership::getFamilyId)
                .findFirst()
                .map(this::planOfFamily);
    }

    private FamilyPlanView planOfFamily(UUID familyId) {
        EntitlementService.FamilyPlan effective = entitlementService.effectivePlan(familyId);
        boolean ends = effective.fromSubscription()
                && effective.subscription().map(s -> s.getCurrentPeriodEnd() != null).orElse(false);
        Optional<PlanOffer> afterwards = ends ? Optional.of(offer(entitlementService.defaultPlan())) : Optional.empty();
        List<PlanOffer> choices = plans.findAllByOrderByCreatedAtAsc().stream()
                .filter(plan -> plan.getAudience() == PlanAudience.FAMILY)
                .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE && plan.isSelfService())
                .filter(plan -> !plan.getId().equals(effective.plan().getId()))
                .map(this::offer)
                .toList();
        return new FamilyPlanView(effective, entitlementService.usage(familyId), afterwards, choices,
                planRequests.openRequestOf(familyId));
    }

    private PlanOffer offer(SubscriptionPlan plan) {
        return new PlanOffer(plan, entitlementService.limitsOf(plan.getId()));
    }

    // ------------------------------------------------------------------

    private FamilyView familyView(UUID familyId) {
        Family family = families.findById(familyId).orElseThrow(() -> new ResourceNotFoundException("Family", familyId));
        List<ParentRef> parents = memberships.findByFamilyId(familyId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(membership -> accounts.findById(membership.getAccountId())
                        .map(account -> new ParentRef(account.getId(), account.getEmail())).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new FamilyView(familyId, family.getName(), parents, (int) children.countByFamilyId(familyId),
                entitlementService.effectivePlan(familyId), entitlementService.usage(familyId));
    }

    private PlanView view(SubscriptionPlan plan) {
        long subscribers = plan.getAudience() == PlanAudience.SCHOOL ? schoolSubscriptions.countByPlanId(plan.getId())
                : subscriptions.countByPlanId(plan.getId());
        return new PlanView(plan, entitlementService.limitsOf(plan.getId()), subscribers);
    }

    private void recordSchool(UUID adminId, String action, UUID resourceId, Map<String, Object> metadata) {
        audit.record(new RecordAuditEvent(null, adminId, AuditActorType.ADMIN, action, "SCHOOL_SUBSCRIPTION", resourceId, metadata));
    }

    private SubscriptionPlan plan(UUID planId) {
        return plans.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
    }

    private void record(UUID adminId, String action, UUID resourceId, Map<String, Object> metadata) {
        String resourceType = action.startsWith("PLAN_") ? "SUBSCRIPTION_PLAN" : RESOURCE;
        audit.record(new RecordAuditEvent(null, adminId, AuditActorType.ADMIN, action, resourceType, resourceId, metadata));
    }

    private Account requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
        return account;
    }
}
