package com.platform.subscription.service;

import com.platform.child.entity.ChildStatus;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.repository.FamilyRepository;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.EnrollmentStatus;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.FamilySubscription;
import com.platform.subscription.entity.PlanEntitlement;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.FamilySubscriptionRepository;
import com.platform.subscription.repository.PlanEntitlementRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What a family may have now (PLATFORM-EXPANSION.md §3). The effective plan is the one its
 * subscription grants at this moment, or the platform's default plan - resolved on every read,
 * so an ended period takes effect without any scheduled job. Enforcement happens only where a
 * family adds something (a child, a journey); nothing a child has already done is ever taken away.
 */
@Service
public class EntitlementService {

    private final SubscriptionPlanRepository plans;
    private final PlanEntitlementRepository entitlements;
    private final FamilySubscriptionRepository subscriptions;
    private final FamilyRepository families;
    private final ChildRepository children;
    private final EnrollmentRepository enrollments;

    public EntitlementService(SubscriptionPlanRepository plans, PlanEntitlementRepository entitlements,
                              FamilySubscriptionRepository subscriptions, FamilyRepository families,
                              ChildRepository children, EnrollmentRepository enrollments) {
        this.plans = plans;
        this.entitlements = entitlements;
        this.subscriptions = subscriptions;
        this.families = families;
        this.children = children;
        this.enrollments = enrollments;
    }

    /**
     * @param subscription the family's subscription row, if it has one (even one no longer granting access)
     * @param fromSubscription whether {@code plan} comes from it (false: the default plan applies)
     * @param limits       every entitlement of the plan; a {@code null} value is unlimited
     */
    public record FamilyPlan(SubscriptionPlan plan, Optional<FamilySubscription> subscription, boolean fromSubscription,
                             Map<EntitlementKey, Integer> limits) {

        public Integer limit(EntitlementKey key) {
            return limits.get(key);
        }
    }

    @Transactional(readOnly = true)
    public FamilyPlan effectivePlan(UUID familyId) {
        return effectivePlan(familyId, Instant.now());
    }

    @Transactional(readOnly = true)
    public FamilyPlan effectivePlan(UUID familyId, Instant at) {
        Optional<FamilySubscription> subscription = subscriptions.findByFamilyId(familyId);
        Optional<SubscriptionPlan> granted = subscription.filter(s -> s.grantsAccessAt(at)).flatMap(s -> plans.findById(s.getPlanId()));
        SubscriptionPlan plan = granted.orElseGet(this::defaultPlan);
        return new FamilyPlan(plan, subscription, granted.isPresent(), limitsOf(plan.getId()));
    }

    public SubscriptionPlan defaultPlan() {
        // V42 seeds one and the plan service never leaves the platform without one.
        return plans.findByDefaultPlanTrue().orElseThrow(() -> new IllegalStateException("No default subscription plan is configured"));
    }

    public Map<EntitlementKey, Integer> limitsOf(UUID planId) {
        Map<EntitlementKey, Integer> limits = new EnumMap<>(EntitlementKey.class);
        for (PlanEntitlement entitlement : entitlements.findByPlanId(planId)) {
            limits.put(entitlement.getKey(), entitlement.getLimitValue());
        }
        return limits;
    }

    /** How much of each limited thing a family uses now. */
    @Transactional(readOnly = true)
    public Map<EntitlementKey, Integer> usage(UUID familyId) {
        Map<EntitlementKey, Integer> usage = new EnumMap<>(EntitlementKey.class);
        usage.put(EntitlementKey.CHILDREN_MAX, (int) children.countByFamilyIdAndStatus(familyId, ChildStatus.ACTIVE));
        int busiest = children.findByFamilyId(familyId).stream().mapToInt(child -> journeysOf(child.getId())).max().orElse(0);
        usage.put(EntitlementKey.JOURNEYS_PER_CHILD_MAX, busiest);
        return usage;
    }

    /**
     * Called before a family adds a child. Locks the family row, so two parents adding children
     * at the same moment can't both pass a limit of one remaining place.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRoomForChild(UUID familyId) {
        families.findByIdForUpdate(familyId).orElseThrow(() -> new ResourceNotFoundException("Family", familyId));
        Integer limit = effectivePlan(familyId).limit(EntitlementKey.CHILDREN_MAX);
        // A child being deleted no longer takes a place.
        if (limit != null && children.countByFamilyIdAndStatus(familyId, ChildStatus.ACTIVE) >= limit) {
            throw new PlanLimitException(EntitlementKey.CHILDREN_MAX, limit);
        }
    }

    /** Called before a family starts a child on another journey. Locks the child row (same reason). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRoomForJourney(UUID childId) {
        Child child = children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        Integer limit = effectivePlan(child.getFamilyId()).limit(EntitlementKey.JOURNEYS_PER_CHILD_MAX);
        if (limit != null && journeysOf(childId) >= limit) {
            throw new PlanLimitException(EntitlementKey.JOURNEYS_PER_CHILD_MAX, limit);
        }
    }

    /** Journeys a child is following: active or paused enrolments (finished and withdrawn ones don't count). */
    private int journeysOf(UUID childId) {
        return (int) enrollments.findByChildId(childId).stream()
                .map(Enrollment::getStatus)
                .filter(status -> status == EnrollmentStatus.ACTIVE || status == EnrollmentStatus.PAUSED)
                .count();
    }
}
