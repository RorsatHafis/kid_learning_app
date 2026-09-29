package com.platform.subscription.repository;

import com.platform.subscription.entity.FamilySubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface FamilySubscriptionRepository extends JpaRepository<FamilySubscription, UUID> {

    Optional<FamilySubscription> findByFamilyId(UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FamilySubscription s where s.familyId = :familyId")
    Optional<FamilySubscription> findByFamilyIdForUpdate(@Param("familyId") UUID familyId);

    long countByPlanId(UUID planId);
}
