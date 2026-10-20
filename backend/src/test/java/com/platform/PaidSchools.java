package com.platform;

import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.payment.service.PaymentService;
import com.platform.subscription.entity.BillingInterval;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import com.platform.subscription.service.SubscriptionService;
import org.springframework.context.ApplicationContext;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test fixture (Y44): makes a school paid the way a real one is - an administrator invoices it at a priced school
 * plan and confirms the transfer against a receipt reference - so tests of school features run against a paid
 * school, and the paid-school rule itself is never bypassed. The plan is unlimited so it limits nothing else.
 */
public final class PaidSchools {

    private static final String PLAN_CODE = "TEST_PAID_SCHOOL";

    private PaidSchools() {

    }

    /** Pays for {@code schoolId} for one year; returns the confirmed order's id. */
    public static UUID pay(ApplicationContext context, UUID schoolId) {

        UUID adminId = admin(context);
        UUID planId = plan(context, adminId);
        PaymentService payments = context.getBean(PaymentService.class);
        UUID orderId = payments.invoiceSchool(adminId, schoolId, planId, "test-paid-school-" + UUID.randomUUID()).order().getId();
        payments.confirmManual(adminId, orderId, "TEST-RECEIPT-" + schoolId, "Test fixture");
        return orderId;

    }

    private static UUID admin(ApplicationContext context) {

        AccountRepository accounts = context.getBean(AccountRepository.class);
        String email = "paid-schools-admin@example.test";
        return accounts.findByNormalizedEmail(Account.normalize(email))
                .orElseGet(() -> accounts.saveAndFlush(Account.register(email, "{noop}not-used-for-sign-in", PlatformRole.ADMIN)))
                .getId();

    }

    private static synchronized UUID plan(ApplicationContext context, UUID adminId) {

        SubscriptionPlanRepository plans = context.getBean(SubscriptionPlanRepository.class);
        return plans.findByCode(PLAN_CODE).map(plan -> plan.getId()).orElseGet(() -> {
            SubscriptionService subscriptions = context.getBean(SubscriptionService.class);
            UUID id = subscriptions.createPlan(adminId, PLAN_CODE, "Paid school (test)", "Test fixture", PlanAudience.SCHOOL).plan().getId();
            Map<EntitlementKey, Integer> unlimited = new EnumMap<>(EntitlementKey.class);
            for (EntitlementKey key : new EntitlementKey[] {EntitlementKey.SCHOOL_CHILDREN_MAX, EntitlementKey.SCHOOL_TEACHERS_MAX,
                    EntitlementKey.SCHOOL_CLASSES_MAX, EntitlementKey.TEACHER_STORAGE_MB}) {
                unlimited.put(key, null);
            }
            subscriptions.revisePlan(adminId, id, "Paid school (test)", "Test fixture", false, 1000L, "USD", BillingInterval.YEAR, unlimited);
            subscriptions.activatePlan(adminId, id);
            return id;
        });

    }

}
