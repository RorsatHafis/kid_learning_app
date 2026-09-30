package com.platform.subscription.service;

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

    public SubscriptionService(SubscriptionPlanRepository plans, PlanEntitlementRepository entitlements,
                               FamilySubscriptionRepository subscriptions, FamilyRepository families,
                               MembershipRepository memberships, AccountRepository accounts, ChildRepository children,
                               EntitlementService entitlementService, AuditService audit) {
        this.plans = plans;
        this.entitlements = entitlements;
        this.subscriptions = subscriptions;
        this.families = families;
        this.memberships = memberships;
        this.accounts = accounts;
        this.children = children;
        this.entitlementService = entitlementService;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ views

    public record PlanView(SubscriptionPlan plan, Map<EntitlementKey, Integer> limits, long familiesOnPlan) {
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
                                 Optional<PlanOffer> afterwards, List<PlanOffer> choices) {
    }

    // ------------------------------------------------------------------ plans

    @Transactional(readOnly = true)
    public List<PlanView> listPlans(UUID adminId) {
        requireAdmin(adminId);
        return plans.findAllByOrderByCreatedAtAsc().stream()
                .map(plan -> new PlanView(plan, entitlementService.limitsOf(plan.getId()), subscriptions.countByPlanId(plan.getId())))
                .toList();
    }

    /** A new plan starts as a draft with the default plan's limits, so nothing is left unset by accident. */
    @Transactional
    public PlanView createPlan(UUID adminId, String code, String name, String description) {
        requireAdmin(adminId);
        String normalized = code == null ? "" : code.strip().toUpperCase();
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("A plan code uses capital letters, numbers and _ (2–60 characters)");
        }
        if (plans.findByCode(normalized).isPresent()) {
            throw new IllegalStateException("A plan with the code " + normalized + " already exists");
        }
        SubscriptionPlan plan = plans.save(SubscriptionPlan.draft(normalized, name, description));
        Map<EntitlementKey, Integer> start = entitlementService.limitsOf(entitlementService.defaultPlan().getId());
        for (EntitlementKey key : EntitlementKey.values()) {
            entitlements.save(PlanEntitlement.of(plan.getId(), key, start.get(key)));
        }
        record(adminId, "PLAN_CREATED", plan.getId(), Map.of("code", normalized));
        return view(plan);
    }

    @Transactional
    public PlanView revisePlan(UUID adminId, UUID planId, String name, String description, boolean selfService,
                               Long priceAmountMinor, String priceCurrency, BillingInterval interval,
                               Map<EntitlementKey, Integer> limits) {
        requireAdmin(adminId);
        SubscriptionPlan plan = plan(planId);
        plan.revise(name, description, selfService, priceAmountMinor, priceCurrency, interval);
        for (Map.Entry<EntitlementKey, Integer> entry : limits.entrySet()) {
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
        for (EntitlementKey key : EntitlementKey.values()) {
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

    /** Families without a subscription get the default plan; changing it changes what they can add from now on. */
    @Transactional
    public PlanView makeDefault(UUID adminId, UUID planId) {
        requireAdmin(adminId);
        SubscriptionPlan plan = plan(planId);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalStateException("Only an active plan can be the default");
        }
        Optional<SubscriptionPlan> current = plans.findDefaultForUpdate();
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

    // ------------------------------------------------------------------ the family's own view

    /** The signed-in parent's family plan: what it includes and what the family uses. */
    @Transactional(readOnly = true)
    public FamilyPlanView familyPlan(UUID accountId) {
        UUID familyId = memberships.findByAccountId(accountId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(Membership::getFamilyId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Family for account", accountId));
        EntitlementService.FamilyPlan effective = entitlementService.effectivePlan(familyId);
        boolean ends = effective.fromSubscription()
                && effective.subscription().map(s -> s.getCurrentPeriodEnd() != null).orElse(false);
        Optional<PlanOffer> afterwards = ends ? Optional.of(offer(entitlementService.defaultPlan())) : Optional.empty();
        List<PlanOffer> choices = plans.findAllByOrderByCreatedAtAsc().stream()
                .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE && plan.isSelfService())
                .filter(plan -> !plan.getId().equals(effective.plan().getId()))
                .map(this::offer)
                .toList();
        return new FamilyPlanView(effective, entitlementService.usage(familyId), afterwards, choices);
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
        return new PlanView(plan, entitlementService.limitsOf(plan.getId()), subscriptions.countByPlanId(plan.getId()));
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
