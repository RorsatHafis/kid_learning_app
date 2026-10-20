package com.platform.subscription.service;

import com.platform.common.entity.CatalogStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.payment.config.PaymentProperties;
import com.platform.payment.entity.PaymentStatus;
import com.platform.payment.repository.PaymentOrderRepository;
import com.platform.school.entity.SchoolChildAccessStatus;
import com.platform.school.entity.SchoolStaff;
import com.platform.school.entity.SchoolStaffStatus;
import com.platform.school.repository.SchoolChildAccessRepository;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.repository.SchoolStaffRepository;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.PlanStatus;
import com.platform.subscription.entity.SchoolSubscription;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.entity.SubscriptionSource;
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
 * What a school may use and hold now (V59, Y44), the school counterpart of {@link EntitlementService}.
 * <p>
 * <b>School services are paid (Y44).</b> A school may use them only while a subscription backed by a
 * confirmed payment is in force: a provider-sourced subscription within its period, on an active school
 * plan, and a succeeded payment order whose paid period covers this moment. A plan given by an
 * administrator, the schools' default plan, a requested plan or a pending, failed, cancelled, expired or
 * refunded payment grants nothing; a test payment counts only where test payments are switched on (never
 * in production). Nothing the school holds is deleted when it isn't paid.
 * <p>
 * Limits apply only where a school adds something - a teacher, a class in use, a child - under a lock on
 * the school row, so two requests can't both take its last place. Over a limit after a downgrade, a
 * school keeps what it has and can't add more until it is back under.
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
    private final PaymentOrderRepository orders;
    private final PaymentProperties payments;

    public SchoolEntitlementService(SubscriptionPlanRepository plans, SchoolSubscriptionRepository subscriptions,
                                    EntitlementService entitlements, SchoolRepository schools, SchoolStaffRepository staff,
                                    SchoolClassRepository classes, SchoolChildAccessRepository access,
                                    PaymentOrderRepository orders, PaymentProperties payments) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.schools = schools;
        this.staff = staff;
        this.classes = classes;
        this.access = access;
        this.orders = orders;
        this.payments = payments;
    }

    /**
     * Whether the school may use its school services now (Y44).
     *
     * @param paid           a confirmed, paid subscription is in force
     * @param paidUntil      when the paid period ends (null when not paid)
     * @param paymentPending a payment of the school is waiting to be confirmed (it grants nothing yet)
     */
    public record SchoolAccess(boolean paid, Instant paidUntil, boolean paymentPending) {
    }

    @Transactional(readOnly = true)
    public SchoolAccess access(UUID schoolId) {

        Instant now = Instant.now();
        Optional<SchoolSubscription> paid = paidSubscription(schoolId, now);
        boolean pending = orders.findFirstBySchoolIdAndStatus(schoolId, PaymentStatus.PENDING).filter(o -> !o.isPastExpiry(now)).isPresent();
        return new SchoolAccess(paid.isPresent(), paid.map(SchoolSubscription::getCurrentPeriodEnd).orElse(null), pending);

    }

    /** Refuses a school service (402) unless the school's paid subscription is in force. */
    @Transactional(readOnly = true)
    public void requirePaid(UUID schoolId) {

        if (paidSubscription(schoolId, Instant.now()).isEmpty()) {
            throw new SchoolPaymentRequiredException(schoolId);
        }

    }

    /** The subscription that makes the school paid at {@code at}, if one does. */
    private Optional<SchoolSubscription> paidSubscription(UUID schoolId, Instant at) {

        return subscriptions.findBySchoolId(schoolId)
                .filter(s -> s.getSource() == SubscriptionSource.PROVIDER && s.grantsAccessAt(at))
                .filter(s -> plans.findById(s.getPlanId())
                        .filter(plan -> plan.getAudience() == PlanAudience.SCHOOL && plan.getStatus() != PlanStatus.DRAFT)
                        .isPresent())
                .filter(s -> orders.schoolPaidAt(schoolId, at, payments.devSimulator().enabled()));

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
        // Y44: only a paid subscription gives a school its plan.
        Optional<SubscriptionPlan> granted = paidSubscription(schoolId, at).flatMap(s -> plans.findById(s.getPlanId()));
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
