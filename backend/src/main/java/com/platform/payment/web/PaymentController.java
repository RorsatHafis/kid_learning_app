package com.platform.payment.web;

import com.platform.payment.entity.PaymentStatus;
import com.platform.payment.service.PaymentService;
import com.platform.payment.web.PaymentDtos.ConfirmRequest;
import com.platform.payment.web.PaymentDtos.InvoiceRequest;
import com.platform.payment.web.PaymentDtos.MethodsResponse;
import com.platform.payment.web.PaymentDtos.NoteRequest;
import com.platform.payment.web.PaymentDtos.OrderPage;
import com.platform.payment.web.PaymentDtos.OrderResponse;
import com.platform.payment.web.PaymentDtos.ReasonRequest;
import com.platform.payment.web.PaymentDtos.SimulateRequest;
import com.platform.payment.web.PaymentDtos.StartPaymentRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Payments over HTTP (Y43): a family paying for its plan, a principal seeing the school's invoices, and an
 * administrator invoicing schools and settling transfers. Who may act on an order is decided in PaymentService.
 */
@RestController
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {

        this.payments = payments;

    }

    // ------------------------------------------------------------------ families and principals

    @GetMapping("/api/v1/payments/methods")
    public MethodsResponse methods() {

        return new MethodsResponse(payments.familyMethods());

    }

    @GetMapping("/api/v1/family/payments")
    public List<OrderResponse> familyPayments(@AuthenticationPrincipal UUID accountId) {

        return payments.familyPayments(accountId).stream().map(order -> OrderResponse.summary(order, false)).toList();

    }

    @PostMapping("/api/v1/family/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse startFamilyPayment(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody StartPaymentRequest request) {

        return OrderResponse.of(payments.startFamilyPayment(accountId, request.planId(), request.method(), request.idempotencyKey()), false);

    }

    @GetMapping("/api/v1/schools/{schoolId}/payments")
    public List<OrderResponse> schoolPayments(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId) {

        return payments.schoolPayments(accountId, schoolId).stream().map(order -> OrderResponse.summary(order, false)).toList();

    }

    /** Y44: the school plans its principal may pay for, and starting that payment (open while the school is unpaid). */
    @GetMapping("/api/v1/schools/{schoolId}/payment-plans")
    public List<PaymentDtos.SchoolPlanChoice> schoolPlanChoices(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId) {

        return payments.schoolPlanChoices(accountId, schoolId).stream()
                .map(plan -> PaymentDtos.SchoolPlanChoice.of(plan, payments.limitsOf(plan.getId()))).toList();

    }

    @PostMapping("/api/v1/schools/{schoolId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse startSchoolPayment(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId,
                                            @Valid @RequestBody StartPaymentRequest request) {

        return OrderResponse.of(payments.startSchoolPayment(accountId, schoolId, request.planId(), request.method(), request.idempotencyKey()),
                false);

    }

    @GetMapping("/api/v1/payments/{orderId}")
    public OrderResponse order(@AuthenticationPrincipal UUID accountId, @PathVariable UUID orderId) {

        return OrderResponse.of(payments.order(accountId, orderId), false);

    }

    @PostMapping("/api/v1/payments/{orderId}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal UUID accountId, @PathVariable UUID orderId) {

        return OrderResponse.of(payments.cancel(accountId, orderId), false);

    }

    @PostMapping("/api/v1/payments/{orderId}/note")
    public OrderResponse note(@AuthenticationPrincipal UUID accountId, @PathVariable UUID orderId, @Valid @RequestBody NoteRequest request) {

        return OrderResponse.of(payments.noteFromCustomer(accountId, orderId, request.note()), false);

    }

    /** Development and tests only (refused unless the simulator is switched on, which production never allows). */
    @PostMapping("/api/v1/payments/{orderId}/simulate")
    public OrderResponse simulate(@AuthenticationPrincipal UUID accountId, @PathVariable UUID orderId,
                                  @Valid @RequestBody SimulateRequest request) {

        return OrderResponse.of(payments.simulate(accountId, orderId, request.outcome()), false);

    }

    // ------------------------------------------------------------------ administrators

    @GetMapping("/api/v1/admin/payments")
    public OrderPage list(@AuthenticationPrincipal UUID adminId, @RequestParam(required = false) PaymentStatus status,
                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {

        Page<com.platform.payment.entity.PaymentOrder> found = payments.list(adminId, status, page, size);
        return new OrderPage(found.getContent().stream().map(order -> OrderResponse.summary(order, true)).toList(),
                found.getTotalElements(), found.getNumber(), found.getSize());

    }

    @GetMapping("/api/v1/admin/payments/{orderId}")
    public OrderResponse adminOrder(@AuthenticationPrincipal UUID adminId, @PathVariable UUID orderId) {

        return OrderResponse.of(payments.adminOrder(adminId, orderId), true);

    }

    @PostMapping("/api/v1/admin/payments/{orderId}/confirm")
    public OrderResponse confirm(@AuthenticationPrincipal UUID adminId, @PathVariable UUID orderId,
                                 @Valid @RequestBody ConfirmRequest request) {

        return OrderResponse.of(payments.confirmManual(adminId, orderId, request.receiptReference(), request.note()), true);

    }

    @PostMapping("/api/v1/admin/payments/{orderId}/reject")
    public OrderResponse reject(@AuthenticationPrincipal UUID adminId, @PathVariable UUID orderId,
                                @Valid @RequestBody ReasonRequest request) {

        return OrderResponse.of(payments.rejectManual(adminId, orderId, request.reason()), true);

    }

    @PostMapping("/api/v1/admin/payments/{orderId}/refund")
    public OrderResponse refund(@AuthenticationPrincipal UUID adminId, @PathVariable UUID orderId,
                                @Valid @RequestBody ReasonRequest request) {

        return OrderResponse.of(payments.refund(adminId, orderId, request.reason()), true);

    }

    @PostMapping("/api/v1/admin/payments/{orderId}/reconcile")
    public OrderResponse reconcile(@AuthenticationPrincipal UUID adminId, @PathVariable UUID orderId) {

        return OrderResponse.of(payments.reconcile(adminId, orderId), true);

    }

    @PostMapping("/api/v1/admin/schools/{schoolId}/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse invoiceSchool(@AuthenticationPrincipal UUID adminId, @PathVariable UUID schoolId,
                                       @Valid @RequestBody InvoiceRequest request) {

        return OrderResponse.of(payments.invoiceSchool(adminId, schoolId, request.planId(), request.idempotencyKey()), true);

    }

}
