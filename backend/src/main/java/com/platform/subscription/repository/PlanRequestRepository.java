package com.platform.subscription.repository;

import com.platform.subscription.entity.PlanRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for families' requests for a plan: the open request (locked when answering) and one by id.
 */
public interface PlanRequestRepository extends JpaRepository<PlanRequest, UUID> {

    /** The family's open request, row-locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlanRequest r where r.familyId = :familyId and r.status = com.platform.subscription.entity.PlanRequest.Status.OPEN")
    Optional<PlanRequest> findOpenForUpdate(@Param("familyId") UUID familyId);

    @Query("select r from PlanRequest r where r.familyId = :familyId and r.status = com.platform.subscription.entity.PlanRequest.Status.OPEN")
    Optional<PlanRequest> findOpen(@Param("familyId") UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlanRequest r where r.id = :id")
    Optional<PlanRequest> findByIdForUpdate(@Param("id") UUID id);

}
