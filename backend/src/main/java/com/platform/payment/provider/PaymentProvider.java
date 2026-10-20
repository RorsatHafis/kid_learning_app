package com.platform.payment.provider;

import com.platform.payment.entity.PaymentOrder;
import com.platform.payment.entity.PaymentProviderType;
import com.platform.payment.entity.PaymentStatus;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The boundary between the payment lifecycle (PaymentService) and one way of taking money. A provider says
 * whether it is available here, what the customer needs to pay an order, and - when it can be asked - the
 * order's status from its own trusted source. It never decides access: only PaymentService turns a confirmed
 * status into a subscription.
 */
public interface PaymentProvider {

    PaymentProviderType type();

    /** Configured and permitted in this deployment. */
    boolean available();

    /** How long an order may stay pending with this provider. */
    Duration lifetime();

    /** What the customer needs to pay this order now (instructions, or a form for the provider's checkout). */
    Checkout checkout(PaymentOrder order);

    /** The order's status from the provider's own record; empty when this provider can't be asked (manual, simulator). */
    Optional<TrustedStatus> check(PaymentOrder order);

    /**
     * @param instructions words for the customer (manual payment's payee details, or the simulator's warning)
     * @param action       where the customer's browser posts {@code fields} to pay (a hosted checkout), or null
     * @param fields       the signed form for {@code action}; never contains a secret
     */
    record Checkout(String instructions, String action, Map<String, String> fields) {

        public static Checkout instructions(String text) {

            return new Checkout(text, null, Map.of());

        }

    }

    /**
     * A status the provider itself reported when asked - the only kind that can settle an online payment.
     *
     * @param eventId identifies this answer, so the same answer arriving twice is recorded once
     */
    record TrustedStatus(PaymentStatus status, long amountMinor, String currency, String providerReference, String eventId) {
    }

}
