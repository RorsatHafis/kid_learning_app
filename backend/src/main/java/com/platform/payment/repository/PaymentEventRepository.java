package com.platform.payment.repository;

import com.platform.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** The payment ledger (V62, append-only): read back per order; a provider event id is recorded once. */
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    List<PaymentEvent> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    boolean existsByOrderIdAndProviderEventId(UUID orderId, String providerEventId);

}
