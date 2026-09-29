package com.platform.subscription.repository;

import com.platform.subscription.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByDefaultPlanTrue();

    Optional<SubscriptionPlan> findByCode(String code);

    List<SubscriptionPlan> findAllByOrderByCreatedAtAsc();

    /** Serializes changes to which plan is the default. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SubscriptionPlan p where p.defaultPlan = true")
    Optional<SubscriptionPlan> findDefaultForUpdate();
}
