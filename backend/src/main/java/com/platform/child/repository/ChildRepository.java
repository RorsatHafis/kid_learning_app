package com.platform.child.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.platform.child.entity.Child;

public interface ChildRepository extends JpaRepository<Child, UUID> {

    List<Child> findByFamilyId(UUID familyId);

    long countByFamilyId(UUID familyId);

    long countByFamilyIdAndStatus(UUID familyId, com.platform.child.entity.ChildStatus status);

    /** Serializes plan-limited changes to a child (starting journeys) so a limit can't be overrun by a race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Child c where c.id = :id")
    Optional<Child> findByIdForUpdate(@Param("id") UUID id);

}
