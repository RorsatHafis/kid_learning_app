package com.platform.family.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.platform.family.entity.Family;

public interface FamilyRepository extends JpaRepository<Family, UUID> {

    /** Serializes plan-limited changes to a family (adding children) so a limit can't be overrun by a race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Family f where f.id = :id")
    Optional<Family> findByIdForUpdate(@Param("id") UUID id);
}
