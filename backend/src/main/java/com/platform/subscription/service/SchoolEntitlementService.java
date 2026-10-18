package com.platform.subscription.service;

import com.platform.common.entity.CatalogStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.school.entity.SchoolChildAccessStatus;
import com.platform.school.entity.SchoolStaff;
import com.platform.school.entity.SchoolStaffStatus;
import com.platform.school.repository.SchoolChildAccessRepository;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.repository.SchoolStaffRepository;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.SchoolSubscription;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.SchoolSubscriptionRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What a school may hold now (V59), the school counterpart of {@link EntitlementService}: its
 * effective plan is the one its subscription grants at this moment, or the schools' default plan,
 * resolved on every read. Limits apply only where a school adds something - a teacher, a class in
 * use, a child - under a lock on the school row, so two requests can't both take its last place.
 * Nothing a school already has is ever taken away: over a limit after a downgrade, it keeps what it
 * has and can't add more until it is back under.
 */
@Service
public class SchoolEntitlementService {

    /** Staff who hold a teacher's place: on the staff, or invited and not yet answered. */
    private static final List<SchoolStaffStatus> SEATED = List.of(SchoolStaffStatus.ACTIVE, SchoolStaffStatus.INVITED);

    private final SubscriptionPlanRepository plans;
    private final SchoolSubscriptionRepository subscriptions;
    private final EntitlementService entitlements;
    private final SchoolRepository schools;
    private final SchoolStaffRepository staff;
    private final SchoolClassRepository classes;
    private final SchoolChildAccessRepository access;

    public SchoolEntitlementService(SubscriptionPlanRepository plans, SchoolSubscriptionRepository subscriptions,
                                    EntitlementService entitlements, SchoolRepository schools, SchoolStaffRepository staff,
                                    SchoolClassRepository classes, SchoolChildAccessRepository access) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.schools = schools;
        this.staff = staff;
        this.classes = classes;
        this.access = access;
    }

    /**
     * @param subscription     the school's subscription row, if it has one (even one no longer granting access)
     * @param fromSubscription whether {@code plan} comes from it (false: the schools' default plan applies)
     * @param limits           every entitlement of the plan; a {@code null} value is unlimited
     */
    public record SchoolPlan(SubscriptionPlan plan, Optional<SchoolSubscription> subscription, boolean fromSubscription,
                             Map<EntitlementKey, Integer> limits) {

        public Integer limit(EntitlementKey key) {
            return limits.get(key);
        }
    }

    @Transactional(readOnly = true)
    public SchoolPlan effectivePlan(UUID schoolId) {
        return effectivePlan(schoolId, Instant.now());
    }

    @Transactional(readOnly = true)
    public SchoolPlan effectivePlan(UUID schoolId, Instant at) {
        Optional<SchoolSubscription> subscription = subscriptions.findBySchoolId(schoolId);
        Optional<SubscriptionPlan> granted = subscription.filter(s -> s.grantsAccessAt(at)).flatMap(s -> plans.findById(s.getPlanId()));
        SubscriptionPlan plan = granted.orElseGet(() -> entitlements.defaultPlan(PlanAudience.SCHOOL));
        return new SchoolPlan(plan, subscription, granted.isPresent(), entitlements.limitsOf(plan.getId()));
    }

    /** What the school holds of each counted thing now (storage is per teacher: StorageService). */
    @Transactional(readOnly = true)
    public Map<EntitlementKey, Integer> usage(UUID schoolId) {
        Map<EntitlementKey, Integer> usage = new EnumMap<>(EntitlementKey.class);
        usage.put(EntitlementKey.SCHOOL_CHILDREN_MAX, (int) access.countBySchoolIdAndStatus(schoolId, SchoolChildAccessStatus.ACTIVE));
        usage.put(EntitlementKey.SCHOOL_TEACHERS_MAX, (int) staff.countBySchoolIdAndStatusIn(schoolId, SEATED));
        usage.put(EntitlementKey.SCHOOL_CLASSES_MAX, (int) classes.countBySchoolIdAndStatus(schoolId, CatalogStatus.ACTIVE));
        return usage;
    }

    /** Before a principal invites a teacher. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRoomForTeacher(UUID schoolId) {
        lock(schoolId);
        require(schoolId, EntitlementKey.SCHOOL_TEACHERS_MAX, staff.countBySchoolIdAndStatusIn(schoolId, SEATED));
    }

    /** Before a class is created or brought back into use. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRoomForClass(UUID schoolId) {
        lock(schoolId);
        require(schoolId, EntitlementKey.SCHOOL_CLASSES_MAX, classes.countBySchoolIdAndStatus(schoolId, CatalogStatus.ACTIVE));
    }

    /** Before a family gives a school access to a child; a child the school already has takes no new place. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRoomForChild(UUID schoolId, UUID childId) {
        lock(schoolId);
        if (access.findBySchoolIdAndChildIdAndStatus(schoolId, childId, SchoolChildAccessStatus.ACTIVE).isPresent()) {
            return;
        }
        require(schoolId, EntitlementKey.SCHOOL_CHILDREN_MAX, access.countBySchoolIdAndStatus(schoolId, SchoolChildAccessStatus.ACTIVE));
    }

    /**
     * How much media a teacher (or principal) can keep, in MB (empty = unlimited): the most generous
     * allowance among the schools they work in - on the staff, or as its principal; joining a second
     * school never makes it smaller - or the schools' default plan's for someone in no school.
     */
    @Transactional(readOnly = true)
    public OptionalInt teacherStorageMb(UUID accountId) {
        List<UUID> schoolIds = schoolsOf(accountId);
        if (schoolIds.isEmpty()) {
            Integer limit = entitlements.limitsOf(entitlements.defaultPlan(PlanAudience.SCHOOL).getId()).get(EntitlementKey.TEACHER_STORAGE_MB);
            return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
        }
        int most = 0;
        for (UUID schoolId : schoolIds) {
            Integer limit = effectivePlan(schoolId).limit(EntitlementKey.TEACHER_STORAGE_MB);
            if (limit == null) {
                return OptionalInt.empty();
            }
            most = Math.max(most, limit);
        }
        return OptionalInt.of(most);
    }

    /** Whether the account works in any school now (so its storage allowance comes from a school's plan). */
    @Transactional(readOnly = true)
    public boolean inAnySchool(UUID accountId) {
        return !schoolsOf(accountId).isEmpty();
    }

    private List<UUID> schoolsOf(UUID accountId) {
        java.util.stream.Stream<UUID> asStaff = staff.findByAccountIdAndStatus(accountId, SchoolStaffStatus.ACTIVE).stream()
                .map(SchoolStaff::getSchoolId);
        java.util.stream.Stream<UUID> asPrincipal = schools.findByPrincipalAccountId(accountId).stream()
                .filter(school -> !school.isArchived()).map(com.platform.school.entity.School::getId);
        return java.util.stream.Stream.concat(asStaff, asPrincipal).distinct().toList();
    }

    private void lock(UUID schoolId) {
        schools.findByIdForUpdate(schoolId).orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
    }

    private void require(UUID schoolId, EntitlementKey key, long used) {
        Integer limit = effectivePlan(schoolId).limit(key);
        if (limit != null && used >= limit) {
            throw new PlanLimitException(key, limit);
        }
    }
}
