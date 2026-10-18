package com.platform.subscription.repository;

import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    /** The default plan of an audience (V59: one per audience). */
    Optional<SubscriptionPlan> findByAudienceAndDefaultPlanTrue(PlanAudience audience);

    Optional<SubscriptionPlan> findByCode(String code);

    List<SubscriptionPlan> findAllByOrderByCreatedAtAsc();

    /** Serializes changes to which plan is the default. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SubscriptionPlan p where p.defaultPlan = true and p.audience = :audience")
    Optional<SubscriptionPlan> findDefaultForUpdate(@org.springframework.data.repository.query.Param("audience") PlanAudience audience);
}
