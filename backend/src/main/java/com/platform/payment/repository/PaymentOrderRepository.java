package com.platform.payment.repository;

import com.platform.payment.entity.PaymentOrder;
import com.platform.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Payment orders (V62). State changes read the order under a row lock, so two callbacks never both settle it. */
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.id = :id")
    Optional<PaymentOrder> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.reference = :reference")
    Optional<PaymentOrder> findByReferenceForUpdate(@Param("reference") String reference);

    boolean existsByReference(String reference);

    Optional<PaymentOrder> findByCreatedByAccountIdAndIdempotencyKey(UUID createdByAccountId, String idempotencyKey);

    Optional<PaymentOrder> findFirstByFamilyIdAndStatus(UUID familyId, PaymentStatus status);

    Optional<PaymentOrder> findFirstBySchoolIdAndStatus(UUID schoolId, PaymentStatus status);

    List<PaymentOrder> findTop20ByFamilyIdOrderByCreatedAtDesc(UUID familyId);

    List<PaymentOrder> findTop20BySchoolIdOrderByCreatedAtDesc(UUID schoolId);

    Page<PaymentOrder> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);

    Page<PaymentOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<PaymentOrder> findByStatusAndExpiresAtBefore(PaymentStatus status, Instant now);

    /**
     * Whether a confirmed payment of the school covers {@code now} (Y44): a succeeded order whose paid
     * period contains it. A test payment counts only where test payments are switched on.
     */
    @Query("""
            select count(o) > 0 from PaymentOrder o
            where o.schoolId = :schoolId and o.status = com.platform.payment.entity.PaymentStatus.SUCCEEDED
              and o.periodStart <= :now and o.periodEnd > :now
              and (:simulatedCounts = true or o.provider <> com.platform.payment.entity.PaymentProviderType.DEV_SIMULATOR)
            """)
    boolean schoolPaidAt(@Param("schoolId") UUID schoolId, @Param("now") Instant now, @Param("simulatedCounts") boolean simulatedCounts);

}
