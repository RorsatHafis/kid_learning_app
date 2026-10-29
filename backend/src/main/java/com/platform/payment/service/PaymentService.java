package com.platform.payment.service;

import com.platform.common.web.PageResponse;
import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.Membership;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.FamilyRepository;
import com.platform.family.repository.MembershipRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.payment.entity.PaymentCustomerType;
import com.platform.payment.entity.PaymentEvent;
import com.platform.payment.entity.PaymentOrder;
import com.platform.payment.entity.PaymentProviderType;
import com.platform.payment.entity.PaymentStatus;
import com.platform.payment.provider.PaymentProvider;
import com.platform.payment.repository.PaymentEventRepository;
import com.platform.payment.repository.PaymentOrderRepository;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.service.SchoolAccessGuard;
import com.platform.subscription.entity.BillingInterval;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.FamilySubscription;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.PlanStatus;
import com.platform.subscription.entity.SchoolSubscription;
import com.platform.subscription.entity.SubscriptionSource;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.FamilySubscriptionRepository;
import com.platform.subscription.repository.SchoolSubscriptionRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import com.platform.subscription.service.EntitlementService;
import com.platform.subscription.service.PlanRequestService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The payment lifecycle (Y43): opening an order for an eligible, priced plan; settling it from a trusted
 * source - a provider's own answer, or an administrator holding the real receipt; and only then granting
 * the plan through the family's or school's subscription. Amount, currency, period and limits are always
 * the plan's, frozen into the order; nothing a browser sends can set them. Every transition is appended to
 * the payment ledger and the audit log.
 */
@Service
public class PaymentService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentOrderRepository orders;
    private final PaymentEventRepository events;
    private final Map<PaymentProviderType, PaymentProvider> providers = new EnumMap<>(PaymentProviderType.class);
    private final SubscriptionPlanRepository plans;
    private final EntitlementService entitlements;
    private final FamilyRepository families;
    private final MembershipRepository memberships;
    private final SchoolRepository schools;
    private final FamilySubscriptionRepository familySubscriptions;
    private final SchoolSubscriptionRepository schoolSubscriptions;
    private final AccountRepository accounts;
    private final SchoolAccessGuard schoolGuard;
    private final PlanRequestService planRequests;
    private final AuditService audit;
    private final PaymentInstructionsService instructions;

    public PaymentService(PaymentOrderRepository orders, PaymentEventRepository events, List<PaymentProvider> providers,
                          SubscriptionPlanRepository plans, EntitlementService entitlements, FamilyRepository families,
                          MembershipRepository memberships, SchoolRepository schools,
                          FamilySubscriptionRepository familySubscriptions, SchoolSubscriptionRepository schoolSubscriptions,
                          AccountRepository accounts, SchoolAccessGuard schoolGuard, PlanRequestService planRequests,
                          AuditService audit, PaymentInstructionsService instructions) {

        this.instructions = instructions;

        this.orders = orders;
        this.events = events;
        providers.forEach(provider -> this.providers.put(provider.type(), provider));
        this.plans = plans;
        this.entitlements = entitlements;
        this.families = families;
        this.memberships = memberships;
        this.schools = schools;
        this.familySubscriptions = familySubscriptions;
        this.schoolSubscriptions = schoolSubscriptions;
        this.accounts = accounts;
        this.schoolGuard = schoolGuard;
        this.planRequests = planRequests;
        this.audit = audit;

    }

    /** An order with what its customer needs to pay it now (only while it is pending). */
    public record OrderView(PaymentOrder order, PaymentProvider.Checkout checkout, List<PaymentEvent> events) {
    }

    /** The ways of paying this deployment offers a family right now (none: plans can't be bought here yet). */
    public List<PaymentProviderType> familyMethods() {

        return providers.values().stream().filter(PaymentProvider::available).map(PaymentProvider::type).sorted().toList();

    }

    // ------------------------------------------------------------------ families

    /**
     * A parent starts paying for a plan their family may choose. The same request repeated (same key) returns
     * the same order; a second payment can't start while one is in progress.
     */
    @Transactional
    public OrderView startFamilyPayment(UUID accountId, UUID planId, PaymentProviderType method, String idempotencyKey) {

        Optional<PaymentOrder> repeated = orders.findByCreatedByAccountIdAndIdempotencyKey(accountId, idempotencyKey);
        if (repeated.isPresent()) {
            return view(repeated.get(), false);
        }

        UUID familyId = familyOf(accountId);
        families.findByIdForUpdate(familyId).orElseThrow(() -> new ResourceNotFoundException("Family", familyId));
        SubscriptionPlan plan = plans.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        if (plan.getAudience() != PlanAudience.FAMILY) {
            throw new IllegalArgumentException("This is a school plan: schools are invoiced for their plan");
        }

        if (!plan.isSelfService()) {
            throw new IllegalArgumentException("This plan isn't offered for families to choose");
        }

        boolean ownPlan = familySubscriptions.findByFamilyId(familyId)
                .filter(s -> s.getPlanId().equals(planId) && s.grantsAccessAt(now())).isPresent();
        if (!plan.offeredAt(now()) && !ownPlan) {
            throw new IllegalStateException("This plan isn't offered now: today is outside its offer dates");
        }

        PaymentProvider provider = requireAvailable(method);
        expireStale(orders.findFirstByFamilyIdAndStatus(familyId, PaymentStatus.PENDING));
        if (orders.findFirstByFamilyIdAndStatus(familyId, PaymentStatus.PENDING).isPresent()) {
            throw new IllegalStateException("A payment is already in progress: finish or cancel it first");
        }

        PaymentOrder order = open(PaymentCustomerType.FAMILY, familyId, null, accountId, plan, provider, idempotencyKey);
        record(order, "CREATED", null, PaymentStatus.PENDING, accountId, null, Map.of("plan", plan.getCode(), "method", method.name()));
        return view(order, false);

    }

    /** The family's payments, newest first, a page at a time (M7: every one can be reached, none is cut off). */
    @Transactional
    public PageResponse<PaymentOrder> familyPayments(UUID accountId, PageResponse.Request request) {

        UUID familyId = familyOf(accountId);
        expireStale(orders.findFirstByFamilyIdAndStatus(familyId, PaymentStatus.PENDING));
        return page(orders.findByFamilyIdOrderByCreatedAtDescIdDesc(familyId, PageRequest.of(request.page(), request.size())), request);

    }

    // ------------------------------------------------------------------ schools (invoiced by an administrator)

    /** An administrator invoices a school for a priced school plan: paid by transfer, confirmed against the receipt. */
    @Transactional
    public OrderView invoiceSchool(UUID adminId, UUID schoolId, UUID planId, String idempotencyKey) {

        requireAdmin(adminId);
        Optional<PaymentOrder> repeated = orders.findByCreatedByAccountIdAndIdempotencyKey(adminId, idempotencyKey);
        if (repeated.isPresent()) {
            return view(repeated.get(), true);
        }

        schools.findByIdForUpdate(schoolId).orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
        SubscriptionPlan plan = plans.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        if (plan.getAudience() != PlanAudience.SCHOOL) {
            throw new IllegalArgumentException("Only a school plan can be invoiced to a school");
        }

        boolean schoolsOwnPlan = schoolSubscriptions.findBySchoolId(schoolId)
                .filter(s -> s.getSource() == SubscriptionSource.PROVIDER && s.getPlanId().equals(planId)).isPresent();
        if (!plan.offeredAt(now()) && !schoolsOwnPlan) {
            throw new IllegalStateException("This plan isn't offered now: today is outside its offer dates");
        }

        PaymentProvider provider = requireAvailable(PaymentProviderType.MANUAL);
        expireStale(orders.findFirstBySchoolIdAndStatus(schoolId, PaymentStatus.PENDING));
        if (orders.findFirstBySchoolIdAndStatus(schoolId, PaymentStatus.PENDING).isPresent()) {
            throw new IllegalStateException("A payment is already in progress: finish or cancel it first");
        }

        PaymentOrder order = open(PaymentCustomerType.SCHOOL, null, schoolId, adminId, plan, provider, idempotencyKey);
        record(order, "INVOICED", null, PaymentStatus.PENDING, adminId, null, Map.of("plan", plan.getCode()));
        audit(adminId, "SCHOOL_INVOICED", order, Map.of("plan", plan.getCode()));
        return view(order, true);

    }

    /**
     * A school's principal pays for its plan. Renewing (Y44): the plan its last confirmed payment bought, at that
     * plan's price now; it extends from the current paid end, or starts now when the paid period is over. Choosing
     * (M4): a self-service, priced school plan, while no paid plan is running - a school's first plan, or a new one
     * after the last ended; changing plans mid-period is arranged with the Bookeyond team (it is a pricing decision).
     * Open while the school is unpaid - it is how school services start. The same request repeated returns the same
     * order; nothing is given until the payment is confirmed.
     */
    @Transactional
    public OrderView startSchoolPayment(UUID accountId, UUID schoolId, UUID planId, PaymentProviderType method, String idempotencyKey) {

        schoolGuard.requireSchoolBilling(accountId, schoolId);
        Optional<PaymentOrder> repeated = orders.findByCreatedByAccountIdAndIdempotencyKey(accountId, idempotencyKey);
        if (repeated.isPresent()) {
            return view(repeated.get(), false);
        }

        schools.findByIdForUpdate(schoolId).orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
        SubscriptionPlan plan = plans.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        if (plan.getAudience() != PlanAudience.SCHOOL) {
            throw new IllegalArgumentException("Only a school plan can be invoiced to a school");
        }

        Optional<SchoolSubscription> paidPlan = schoolSubscriptions.findBySchoolId(schoolId)
                .filter(s -> s.getSource() == SubscriptionSource.PROVIDER);
        boolean renewal = paidPlan.filter(s -> s.getPlanId().equals(planId)).isPresent();
        if (!renewal) {
            if (!plan.isSelfService()) {
                throw new IllegalArgumentException("This plan isn't offered for schools to choose: ask the Bookeyond team");
            }
            if (paidPlan.filter(s -> s.grantsAccessAt(now())).isPresent()) {
                throw new IllegalStateException("The school's paid plan is still running: to change plans, ask the Bookeyond team");
            }
            if (!plan.offeredAt(now())) {
                throw new IllegalStateException("This plan isn't offered now: today is outside its offer dates");
            }
        }

        PaymentProvider provider = requireAvailable(method);
        expireStale(orders.findFirstBySchoolIdAndStatus(schoolId, PaymentStatus.PENDING));
        if (orders.findFirstBySchoolIdAndStatus(schoolId, PaymentStatus.PENDING).isPresent()) {
            throw new IllegalStateException("A payment is already in progress: finish or cancel it first");
        }

        PaymentOrder order = open(PaymentCustomerType.SCHOOL, null, schoolId, accountId, plan, provider, idempotencyKey);
        record(order, "CREATED", null, PaymentStatus.PENDING, accountId, null,
                Map.of("plan", plan.getCode(), "method", method.name(), "renewal", renewal));
        return view(order, false);

    }

    /**
     * What a principal may pay for now: the school's own paid plan (to renew it), first; and, while no paid plan is
     * running, the self-service school plans (M4). Each one active and priced.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlan> schoolPlanChoices(UUID accountId, UUID schoolId) {

        schoolGuard.requireSchoolBilling(accountId, schoolId);
        Optional<SchoolSubscription> paidPlan = schoolSubscriptions.findBySchoolId(schoolId)
                .filter(s -> s.getSource() == SubscriptionSource.PROVIDER);
        Optional<UUID> own = paidPlan.map(SchoolSubscription::getPlanId);
        boolean running = paidPlan.filter(s -> s.grantsAccessAt(now())).isPresent();
        return plans.findAllByOrderByCreatedAtAsc().stream()
                .filter(plan -> plan.getAudience() == PlanAudience.SCHOOL && plan.getPriceAmountMinor() != null)
                .filter(plan -> plan.getStatus() == com.platform.subscription.entity.PlanStatus.ACTIVE)
                .filter(plan -> own.filter(plan.getId()::equals).isPresent() || (!running && plan.isSelfService() && plan.offeredAt(now())))
                .sorted(java.util.Comparator.comparing((SubscriptionPlan plan) -> own.filter(plan.getId()::equals).isEmpty()))
                .toList();

    }

    /** A plan's limits by key (null: no limit), as a customer compares plans. */
    @Transactional(readOnly = true)
    public Map<String, Integer> limitsOf(UUID planId) {

        Map<String, Integer> limits = new LinkedHashMap<>();
        entitlements.limitsOf(planId).forEach((key, limit) -> limits.put(key.name(), limit));
        return limits;

    }

    /** A school's invoices, newest first, a page at a time, for its principal (or an administrator). */
    @Transactional
    public PageResponse<PaymentOrder> schoolPayments(UUID accountId, UUID schoolId, PageResponse.Request request) {

        schoolGuard.requireSchoolBilling(accountId, schoolId);
        expireStale(orders.findFirstBySchoolIdAndStatus(schoolId, PaymentStatus.PENDING));
        return page(orders.findBySchoolIdOrderByCreatedAtDescIdDesc(schoolId, PageRequest.of(request.page(), request.size())), request);

    }

    private static PageResponse<PaymentOrder> page(Page<PaymentOrder> found, PageResponse.Request request) {

        return new PageResponse<>(found.getContent(), request.page(), request.size(), found.getTotalElements());

    }

    // ------------------------------------------------------------------ the customer's own order

    @Transactional
    public OrderView order(UUID accountId, UUID orderId) {

        PaymentOrder order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));
        requireCustomerOrAdmin(accountId, order);
        expireStale(Optional.of(order));
        return view(order, false);

    }

    /** The customer stops a payment in progress (a new attempt can start afterwards). */
    @Transactional
    public OrderView cancel(UUID accountId, UUID orderId) {

        PaymentOrder order = lockedOrder(orderId);
        boolean admin = requireCustomerOrAdmin(accountId, order);
        if (order.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only a payment in progress can be cancelled");
        }

        order.cancel(now());
        record(order, "CANCELLED", PaymentStatus.PENDING, PaymentStatus.CANCELLED, accountId, null, Map.of());
        return view(order, admin);

    }

    /** The customer says how they paid (a transfer reference) - kept for the reviewer, never taken as proof. */
    @Transactional
    public OrderView noteFromCustomer(UUID accountId, UUID orderId, String note) {

        PaymentOrder order = lockedOrder(orderId);
        boolean admin = requireCustomerOrAdmin(accountId, order);
        if (order.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only a payment in progress can be changed");
        }

        order.noteFromCustomer(note);
        record(order, "CUSTOMER_NOTE", null, null, accountId, null, Map.of());
        return view(order, admin);

    }

    /**
     * Development and tests only: the simulator's outcome for an order - as a provider's notice would bring it,
     * including the same notice twice (recognised by its id, so it changes nothing the second time).
     */
    @Transactional
    public OrderView simulate(UUID accountId, UUID orderId, PaymentStatus outcome) {

        PaymentOrder order = lockedOrder(orderId);
        boolean admin = requireCustomerOrAdmin(accountId, order);
        if (order.getProvider() != PaymentProviderType.DEV_SIMULATOR) {
            throw new IllegalArgumentException("Only a test payment can be simulated");
        }

        if (!providers.get(PaymentProviderType.DEV_SIMULATOR).available()) {
            throw new IllegalStateException("Test payments are switched off here");
        }

        if (outcome != PaymentStatus.SUCCEEDED && outcome != PaymentStatus.FAILED && outcome != PaymentStatus.CANCELLED) {
            throw new IllegalArgumentException("A test payment can succeed, fail or be cancelled");
        }

        apply(order, new PaymentProvider.TrustedStatus(outcome, order.getAmountMinor(), order.getCurrency(),
                "SIM-" + order.getReference(), "sim:" + outcome.name()), accountId);
        return view(order, admin);

    }

    // ------------------------------------------------------------------ provider notices

    /**
     * A provider's notice about one of our orders. The notice itself isn't trusted (ABA PayWay's isn't signed):
     * the order is settled only from the provider's own answer to our signed check. Unknown references and
     * other providers' orders are ignored without saying so.
     */
    @Transactional
    public void providerNotice(PaymentProviderType type, String reference) {

        if (reference == null || reference.isBlank() || type == PaymentProviderType.DEV_SIMULATOR || type == PaymentProviderType.MANUAL) {
            return;
        }

        Optional<PaymentOrder> found = orders.findByReferenceForUpdate(reference.strip());
        if (found.isEmpty() || found.get().getProvider() != type) {
            return;
        }

        PaymentProvider provider = providers.get(type);
        if (provider == null || !provider.available()) {
            return;
        }

        provider.check(found.get()).ifPresent(status -> apply(found.get(), status, null));

    }

    // ------------------------------------------------------------------ administrators

    @Transactional(readOnly = true)
    public Page<PaymentOrder> list(UUID adminId, PaymentStatus status, int page, int size) {

        return list(adminId, status, null, null, page, size);

    }

    /**
     * The same, for one school ({@code schoolId}) or one family ({@code familyId}) only when given - its invoices and
     * payments, newest first. An administrator reaches a family by a parent's exact address first; this lists nothing
     * a family view doesn't already lead to.
     */
    @Transactional(readOnly = true)
    public Page<PaymentOrder> list(UUID adminId, PaymentStatus status, UUID schoolId, UUID familyId, int page, int size) {

        requireAdmin(adminId);
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        if (schoolId != null) {
            return status == null ? orders.findBySchoolIdOrderByCreatedAtDescIdDesc(schoolId, request)
                    : orders.findBySchoolIdAndStatusOrderByCreatedAtDescIdDesc(schoolId, status, request);
        }
        if (familyId != null) {
            return status == null ? orders.findByFamilyIdOrderByCreatedAtDescIdDesc(familyId, request)
                    : orders.findByFamilyIdAndStatusOrderByCreatedAtDescIdDesc(familyId, status, request);
        }
        return status == null ? orders.findAllByOrderByCreatedAtDesc(request) : orders.findByStatusOrderByCreatedAtDesc(status, request);

    }

    /** An order with its ledger, for an administrator. */
    @Transactional
    public OrderView adminOrder(UUID adminId, UUID orderId) {

        requireAdmin(adminId);
        PaymentOrder order = lockedOrder(orderId);
        expireStale(Optional.of(order));
        return view(order, true);

    }

    /**
     * An administrator confirms a transfer or invoice against the real receipt; only then is the plan given. M8: the
     * amount and currency on the receipt must be the order's own (its frozen price), and one receipt settles one
     * payment - checked under a lock on the receipt, so two confirmations quoting it can't both pass.
     */
    @Transactional
    public OrderView confirmManual(UUID adminId, UUID orderId, String receiptReference, long amountReceivedMinor,
                                   String currencyReceived, String note) {

        requireAdmin(adminId);
        PaymentOrder order = lockedOrder(orderId);
        if (order.getProvider() != PaymentProviderType.MANUAL) {
            throw new IllegalArgumentException("Only a bank transfer or invoice is confirmed by hand");
        }

        if (receiptReference == null || receiptReference.isBlank()) {
            throw new IllegalArgumentException("Enter the receipt's reference to confirm a payment");
        }

        if (!order.getStatus().canStillSucceed()) {
            throw new IllegalStateException("This payment has already been settled");
        }

        if (amountReceivedMinor != order.getAmountMinor() || currencyReceived == null
                || !currencyReceived.strip().equalsIgnoreCase(order.getCurrency())) {
            throw new IllegalStateException("The amount received doesn't match this payment: check the receipt");
        }

        orders.lockReceipt(receiptReference);
        if (orders.receiptUsedElsewhere(order.getProvider(), receiptReference, order.getId())) {
            throw new IllegalStateException("This receipt was already used for another payment");
        }

        PaymentStatus from = order.getStatus();
        fulfil(order, receiptReference.strip(), adminId);
        record(order, "MANUAL_CONFIRMED", from, PaymentStatus.SUCCEEDED, adminId, null, detail("receipt", receiptReference.strip(), "note", note));
        audit(adminId, "PAYMENT_CONFIRMED", order, detail("receipt", receiptReference.strip(), "note", note));
        return view(order, true);

    }

    /** An administrator finds no money arrived for a transfer or invoice. */
    @Transactional
    public OrderView rejectManual(UUID adminId, UUID orderId, String reason) {

        requireAdmin(adminId);
        PaymentOrder order = lockedOrder(orderId);
        if (order.getProvider() != PaymentProviderType.MANUAL) {
            throw new IllegalArgumentException("Only a bank transfer or invoice is confirmed by hand");
        }

        requireReason(reason);
        if (order.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only a payment in progress can be rejected");
        }

        order.fail(now(), reason.strip());
        record(order, "MANUAL_REJECTED", PaymentStatus.PENDING, PaymentStatus.FAILED, adminId, null, detail("reason", reason, null, null));
        audit(adminId, "PAYMENT_REJECTED", order, detail("reason", reason, null, null));
        return view(order, true);

    }

    /**
     * An administrator records that money went back. The access that payment gave is taken back - exactly its
     * period, nothing more. A PayWay payment is refunded in PayWay and recorded here when PayWay reports it.
     */
    @Transactional
    public OrderView refund(UUID adminId, UUID orderId, String reason) {

        requireAdmin(adminId);
        PaymentOrder order = lockedOrder(orderId);
        if (order.getProvider() == PaymentProviderType.ABA_PAYWAY) {
            throw new IllegalArgumentException("Refund a PayWay payment in PayWay: it is recorded here when PayWay reports it");
        }

        requireReason(reason);
        if (order.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Only a payment that succeeded can be refunded");
        }

        reverse(order, adminId, reason.strip());
        return view(order, true);

    }

    /** Asks the provider again (an online payment) and applies its answer; a manual payment only expires if due. */
    @Transactional
    public OrderView reconcile(UUID adminId, UUID orderId) {

        requireAdmin(adminId);
        PaymentOrder order = lockedOrder(orderId);
        PaymentProvider provider = providers.get(order.getProvider());
        if (provider != null && provider.available()) {
            provider.check(order).ifPresent(status -> apply(order, status, adminId));
        }

        expireStale(Optional.of(order));
        return view(order, true);

    }

    /** Pending orders past their lifetime become EXPIRED (run on a schedule, and whenever an order is read). */
    @Transactional
    public int expireDue() {

        List<PaymentOrder> due = orders.findByStatusAndExpiresAtBefore(PaymentStatus.PENDING, now());
        due.forEach(order -> expireStale(Optional.of(order)));
        return due.size();

    }

    // ------------------------------------------------------------------ settling

    /**
     * Applies a provider's trusted answer. The amount and currency must be the order's; a mismatch is recorded
     * for review and changes nothing. A repeated answer (same event id) changes nothing. Money confirmed after
     * an order expired or was cancelled still settles it: it was paid.
     */
    private void apply(PaymentOrder order, PaymentProvider.TrustedStatus status, UUID actor) {

        if (status.eventId() != null && events.existsByOrderIdAndProviderEventId(order.getId(), status.eventId())) {
            return;
        }

        PaymentStatus from = order.getStatus();
        Instant now = now();
        switch (status.status()) {
            case SUCCEEDED -> {
                if (status.amountMinor() != order.getAmountMinor() || !order.getCurrency().equals(status.currency())) {
                    record(order, "AMOUNT_MISMATCH", from, from, actor, status.eventId(),
                            Map.of("reported", status.amountMinor() + " " + status.currency()));
                    return;
                }

                if (!from.canStillSucceed()) {
                    record(order, "NOTICE_IGNORED", from, from, actor, status.eventId(), Map.of("reported", "SUCCEEDED"));
                    return;
                }

                fulfil(order, status.providerReference(), actor);
                record(order, "PROVIDER_CONFIRMED", from, PaymentStatus.SUCCEEDED, actor, status.eventId(), Map.of());
            }
            case FAILED, CANCELLED -> {
                if (from != PaymentStatus.PENDING) {
                    record(order, "NOTICE_IGNORED", from, from, actor, status.eventId(), Map.of("reported", status.status().name()));
                    return;
                }

                if (status.status() == PaymentStatus.FAILED) {
                    order.fail(now, "Declined by the payment provider");
                } else {
                    order.cancel(now);
                }

                record(order, "PROVIDER_" + status.status().name(), from, order.getStatus(), actor, status.eventId(), Map.of());
            }
            case REFUNDED -> {
                if (from == PaymentStatus.SUCCEEDED) {
                    reverse(order, actor, "Refunded at the payment provider");
                    record(order, "PROVIDER_REFUNDED", from, PaymentStatus.REFUNDED, actor, status.eventId(), Map.of());
                }
            }
            default -> {
                // Still pending at the provider: nothing to change.
            }
        }

    }

    /** SUCCEEDED, with the plan given for the paid period - a renewal of the same paid plan extends it. */
    private void fulfil(PaymentOrder order, String providerReference, UUID actor) {

        Instant now = now();
        if (order.getCustomerType() == PaymentCustomerType.FAMILY) {
            if (order.getFamilyId() == null) {
                throw new IllegalStateException("This payment's family no longer exists");
            }

            families.findByIdForUpdate(order.getFamilyId());
            Optional<FamilySubscription> current = familySubscriptions.findByFamilyIdForUpdate(order.getFamilyId());
            Instant start = current.filter(s -> s.getSource() == com.platform.subscription.entity.SubscriptionSource.PROVIDER
                            && s.getPlanId().equals(order.getPlanId()) && s.grantsAccessAt(now) && s.getCurrentPeriodEnd() != null)
                    .map(FamilySubscription::getCurrentPeriodEnd).orElse(now);
            Instant end = plus(start, order.getBillingInterval());
            order.succeed(now, providerReference, start, end);
            if (current.isPresent()) {
                current.get().applyPayment(order.getPlanId(), now, end, order.getProvider().name(), order.getReference());
            } else {
                familySubscriptions.save(FamilySubscription.fromPayment(order.getFamilyId(), order.getPlanId(), now, end,
                        order.getProvider().name(), order.getReference()));
            }

            // A family's open request for this plan is answered when an administrator confirms its payment.
            if (actorType(actor) == AuditActorType.ADMIN) {
                planRequests.onGranted(actor, order.getFamilyId(), order.getPlanId());
            }
        } else {
            schools.findByIdForUpdate(order.getSchoolId());
            Optional<SchoolSubscription> current = schoolSubscriptions.findBySchoolIdForUpdate(order.getSchoolId());
            Instant start = current.filter(s -> s.getSource() == com.platform.subscription.entity.SubscriptionSource.PROVIDER
                            && s.getPlanId().equals(order.getPlanId()) && s.grantsAccessAt(now) && s.getCurrentPeriodEnd() != null)
                    .map(SchoolSubscription::getCurrentPeriodEnd).orElse(now);
            Instant end = plus(start, order.getBillingInterval());
            order.succeed(now, providerReference, start, end);
            if (current.isPresent()) {
                current.get().applyPayment(order.getPlanId(), now, end, order.getProvider().name(), order.getReference());
            } else {
                schoolSubscriptions.save(SchoolSubscription.fromPayment(order.getSchoolId(), order.getPlanId(), now, end,
                        order.getProvider().name(), order.getReference()));
            }
        }

        audit(actor, "SUBSCRIPTION_PAID", order, Map.of("plan", order.getPlanCode(), "until", order.getPeriodEnd().toString()));

    }

    /** REFUNDED, taking back exactly the time this payment gave (never another payment's). */
    private void reverse(PaymentOrder order, UUID actor, String reason) {

        Instant now = now();
        Duration paid = Duration.between(order.getPeriodStart(), order.getPeriodEnd());
        order.refund(now);
        if (order.getCustomerType() == PaymentCustomerType.FAMILY && order.getFamilyId() != null) {
            familySubscriptions.findByFamilyIdForUpdate(order.getFamilyId())
                    .filter(s -> s.getSource() == com.platform.subscription.entity.SubscriptionSource.PROVIDER
                            && s.getPlanId().equals(order.getPlanId()) && s.grantsAccessAt(now))
                    .ifPresent(s -> s.reverseRefund(s.getCurrentPeriodEnd().minus(paid), now));
        } else if (order.getCustomerType() == PaymentCustomerType.SCHOOL) {
            schoolSubscriptions.findBySchoolIdForUpdate(order.getSchoolId())
                    .filter(s -> s.getSource() == com.platform.subscription.entity.SubscriptionSource.PROVIDER
                            && s.getPlanId().equals(order.getPlanId()) && s.grantsAccessAt(now))
                    .ifPresent(s -> s.reverseRefund(s.getCurrentPeriodEnd().minus(paid), now));
        }

        record(order, "REFUNDED", PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDED, actor, null, detail("reason", reason, null, null));
        audit(actor, "PAYMENT_REFUNDED", order, detail("reason", reason, null, null));

    }

    // ------------------------------------------------------------------ helpers

    private PaymentOrder open(PaymentCustomerType type, UUID familyId, UUID schoolId, UUID createdBy, SubscriptionPlan plan,
                              PaymentProvider provider, String idempotencyKey) {

        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalStateException("Only an active plan can be paid for");
        }

        if (plan.getPriceAmountMinor() == null || plan.getPriceAmountMinor() <= 0 || plan.getBillingInterval() == null) {
            throw new IllegalStateException("This plan has no price yet, so it can't be paid for");
        }

        Map<String, Integer> limits = new LinkedHashMap<>();
        for (Map.Entry<EntitlementKey, Integer> entry : entitlements.limitsOf(plan.getId()).entrySet()) {
            limits.put(entry.getKey().name(), entry.getValue());
        }

        PaymentOrder.Offer offer = new PaymentOrder.Offer(plan.getId(), plan.getCode(), plan.getName(), plan.getVersion(),
                plan.getPriceAmountMinor(), plan.getPriceCurrency(), plan.getBillingInterval(), limits);
        PaymentOrder order = PaymentOrder.open(newReference(), type, familyId, schoolId, createdBy, offer, provider.type(),
                idempotencyKey, now().plus(provider.lifetime()));
        if (provider.type() == PaymentProviderType.MANUAL) {
            order.withInstructions(instructions.current().id());
        }
        try {
            return orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException raced) {
            // Two checkouts at once: the database lets one pending payment per customer through.
            throw new IllegalStateException("A payment is already in progress: finish or cancel it first");
        }

    }

    private void expireStale(Optional<PaymentOrder> pending) {

        pending.filter(order -> order.isPastExpiry(now())).ifPresent(order -> {
            order.expire(now());
            record(order, "EXPIRED", PaymentStatus.PENDING, PaymentStatus.EXPIRED, null, null, Map.of());
        });

    }

    private OrderView view(PaymentOrder order, boolean withEvents) {

        PaymentProvider provider = providers.get(order.getProvider());
        PaymentProvider.Checkout checkout = order.getStatus() == PaymentStatus.PENDING && provider != null && provider.available()
                ? provider.checkout(order) : null;
        return new OrderView(order, checkout, withEvents ? events.findByOrderIdOrderByCreatedAtAsc(order.getId()) : List.of());

    }

    private PaymentProvider requireAvailable(PaymentProviderType method) {

        PaymentProvider provider = method == null ? null : providers.get(method);
        if (provider == null || !provider.available()) {
            throw new IllegalStateException("This way of paying isn't available here");
        }

        return provider;

    }

    private PaymentOrder lockedOrder(UUID orderId) {

        return orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));

    }

    /** Whether the caller is an administrator; otherwise they must be the order's family or the school's principal. */
    private boolean requireCustomerOrAdmin(UUID accountId, PaymentOrder order) {

        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() == PlatformRole.ADMIN) {
            return true;
        }

        if (order.getCustomerType() == PaymentCustomerType.FAMILY) {
            boolean member = order.getFamilyId() != null && memberships.findByFamilyIdAndAccountId(order.getFamilyId(), accountId)
                    .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE).isPresent();
            if (!member) {
                throw new AccessDeniedException("Not this family's payment");
            }
        } else {
            schoolGuard.requireSchoolBilling(accountId, order.getSchoolId());
        }

        return false;

    }

    private UUID familyOf(UUID accountId) {

        return memberships.findByAccountId(accountId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(Membership::getFamilyId)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Only a parent in a family can pay for a family plan"));

    }

    private Account requireAdmin(UUID accountId) {

        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }

        return account;

    }

    private static void requireReason(String reason) {

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Give a reason: it is kept with the payment's history");
        }

    }

    /** The time at the database's own precision, so a period shown to the customer is exactly the one stored. */
    private static Instant now() {

        return Instant.now().truncatedTo(ChronoUnit.MICROS);

    }

    private String newReference() {

        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder reference = new StringBuilder("BK");
            for (int i = 0; i < 12; i++) {
                reference.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }

            if (!orders.existsByReference(reference.toString())) {
                return reference.toString();
            }
        }

        throw new IllegalStateException("Could not create a payment reference; try again");

    }

    private static Instant plus(Instant start, BillingInterval interval) {

        return switch (interval) {
            case MONTH -> start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
            case YEAR -> start.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
        };

    }

    private void record(PaymentOrder order, String type, PaymentStatus from, PaymentStatus to, UUID actor, String providerEventId,
                        Map<String, Object> detail) {

        events.save(PaymentEvent.of(order.getId(), type, from, to, actor, providerEventId, detail));

    }

    private void audit(UUID actor, String action, PaymentOrder order, Map<String, Object> metadata) {

        Map<String, Object> details = new LinkedHashMap<>(metadata);
        details.put("reference", order.getReference());
        details.put("amount", order.getAmountMinor() + " " + order.getCurrency());
        audit.record(new RecordAuditEvent(null, actor, actorType(actor), action, "PAYMENT_ORDER", order.getId(), details));

    }

    /** Who acted, as the audit log names them: a provider's notice is the system's. */
    private AuditActorType actorType(UUID actor) {

        if (actor == null) {
            return AuditActorType.SYSTEM;
        }

        return accounts.findById(actor).map(account -> switch (account.getPlatformRole()) {
            case ADMIN -> AuditActorType.ADMIN;
            case PRINCIPAL -> AuditActorType.PRINCIPAL;
            case TEACHER -> AuditActorType.TEACHER;
            default -> AuditActorType.PARENT;
        }).orElse(AuditActorType.SYSTEM);

    }

    private static Map<String, Object> detail(String key, String value, String otherKey, String otherValue) {

        Map<String, Object> detail = new LinkedHashMap<>();
        if (value != null && !value.isBlank()) {
            detail.put(key, value.strip());
        }

        if (otherKey != null && otherValue != null && !otherValue.isBlank()) {
            detail.put(otherKey, otherValue.strip());
        }

        return detail;

    }

}
