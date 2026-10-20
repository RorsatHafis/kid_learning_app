package com.platform.subscription.repository;

import com.platform.subscription.entity.SchoolSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for school subscriptions (one per school), with a locked read for changes, and how many schools a
 * plan has.
 */
public interface SchoolSubscriptionRepository extends JpaRepository<SchoolSubscription, UUID> {

    Optional<SchoolSubscription> findBySchoolId(UUID schoolId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SchoolSubscription s where s.schoolId = :schoolId")
    Optional<SchoolSubscription> findBySchoolIdForUpdate(@Param("schoolId") UUID schoolId);

    long countByPlanId(UUID planId);
}
