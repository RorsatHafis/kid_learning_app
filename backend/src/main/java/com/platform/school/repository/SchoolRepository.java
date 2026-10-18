package com.platform.school.repository;
import com.platform.school.entity.School; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SchoolRepository extends JpaRepository<School, UUID> {
    List<School> findByPrincipalAccountId(UUID principalAccountId);

    /** Serializes what a school adds against its plan (a teacher, a class, a child): see SchoolEntitlementService. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from School s where s.id = :id")
    java.util.Optional<School> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
