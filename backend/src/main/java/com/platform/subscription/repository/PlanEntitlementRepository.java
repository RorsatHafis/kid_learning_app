package com.platform.subscription.repository;

import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.entity.PlanEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for each plan's limits.
 */
public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, UUID> {

    List<PlanEntitlement> findByPlanId(UUID planId);

    List<PlanEntitlement> findByPlanIdIn(List<UUID> planIds);

    Optional<PlanEntitlement> findByPlanIdAndKey(UUID planId, EntitlementKey key);
}
