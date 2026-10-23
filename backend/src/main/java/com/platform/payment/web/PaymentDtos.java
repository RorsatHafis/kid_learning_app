package com.platform.payment.web;

import com.platform.payment.entity.PaymentEvent;
import com.platform.payment.entity.PaymentOrder;
import com.platform.payment.entity.PaymentProviderType;
import com.platform.payment.entity.PaymentStatus;
import com.platform.payment.provider.PaymentProvider;
import com.platform.payment.service.PaymentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment requests and responses (Y43). Requests name a plan and a way of paying - never an amount or a
 * status. Responses show the order as frozen at checkout; the provider's reference and the ledger are for
 * administrators only.
 */
public final class PaymentDtos {

    private PaymentDtos() {

    }

    public record StartPaymentRequest(@NotNull UUID planId, @NotNull PaymentProviderType method,
                                      @NotBlank @Size(max = 80) String idempotencyKey) {
    }

    public record InvoiceRequest(@NotNull UUID planId, @NotBlank @Size(max = 80) String idempotencyKey) {
    }

    public record NoteRequest(@Size(max = 300) String note) {
    }

    public record SimulateRequest(@NotNull PaymentStatus outcome) {
    }

    public record ConfirmRequest(@NotBlank @Size(max = 200) String receiptReference, @Size(max = 300) String note) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record MethodsResponse(List<PaymentProviderType> methods) {
    }

    /**
     * A school plan a principal may pay for (Y44): its price is the plan's own, set by an administrator; its limits
     * (M4: so plans can be compared before paying; null means no limit).
     */
    public record SchoolPlanChoice(UUID id, String code, String name, String description, long amountMinor, String currency,
                                   String billingInterval, java.util.Map<String, Integer> limits) {

        public static SchoolPlanChoice of(com.platform.subscription.entity.SubscriptionPlan plan,
                                          java.util.Map<String, Integer> limits) {

            return new SchoolPlanChoice(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(),
                    plan.getPriceAmountMinor(), plan.getPriceCurrency(), plan.getBillingInterval().name(), limits);

        }

    }

    /** The order as the customer sees it; {@code checkout} only while it can be paid. */
    public record OrderResponse(UUID id, String reference, String customerType, UUID schoolId, String planCode, String planName,
                                long amountMinor, String currency, String billingInterval, Map<String, Integer> limits,
                                PaymentProviderType method, PaymentStatus status, Instant createdAt, Instant expiresAt,
                                Instant completedAt, Instant periodStart, Instant periodEnd, String failureReason,
                                String customerNote, Checkout checkout, AdminDetail admin) {

        public static OrderResponse of(PaymentService.OrderView view, boolean admin) {

            PaymentOrder o = view.order();
            Checkout checkout = view.checkout() == null ? null : Checkout.of(view.checkout());
            AdminDetail detail = admin ? new AdminDetail(o.getFamilyId(), o.getProviderReference(), o.getPlanVersion(),
                    view.events().stream().map(LedgerEntry::of).toList()) : null;
            return new OrderResponse(o.getId(), o.getReference(), o.getCustomerType().name(), o.getSchoolId(), o.getPlanCode(),
                    o.getPlanName(), o.getAmountMinor(), o.getCurrency(), o.getBillingInterval().name(), o.getLimitsSnapshot(),
                    o.getProvider(), o.getStatus(), o.getCreatedAt(), o.getExpiresAt(), o.getCompletedAt(), o.getPeriodStart(),
                    o.getPeriodEnd(), o.getFailureReason(), o.getCustomerNote(), checkout, detail);

        }

        public static OrderResponse summary(PaymentOrder order, boolean admin) {

            return of(new PaymentService.OrderView(order, null, List.of()), admin);

        }

    }

    /** {@code action} and {@code fields}: a form the browser posts to the provider's hosted checkout. */
    public record Checkout(String instructions, String action, Map<String, String> fields) {

        static Checkout of(PaymentProvider.Checkout checkout) {

            return new Checkout(checkout.instructions(), checkout.action(), checkout.fields());

        }

    }

    public record AdminDetail(UUID familyId, String providerReference, long planVersion, List<LedgerEntry> ledger) {
    }

    public record LedgerEntry(String type, PaymentStatus from, PaymentStatus to, UUID actorAccountId, Map<String, Object> detail,
                              Instant at) {

        static LedgerEntry of(PaymentEvent event) {

            return new LedgerEntry(event.getEventType(), event.getFromStatus(), event.getToStatus(), event.getActorAccountId(),
                    event.getDetail(), event.getCreatedAt());

        }

    }

    public record OrderPage(List<OrderResponse> items, long total, int page, int size) {
    }

}
