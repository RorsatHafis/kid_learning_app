package com.platform.school.repository;

import com.platform.school.entity.SchoolChildAccess;
import com.platform.school.entity.SchoolChildAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the access families grant schools: in force for a school and child, a child's grants, and counts
 * against the school's plan.
 */
public interface SchoolChildAccessRepository extends JpaRepository<SchoolChildAccess, UUID> {

    Optional<SchoolChildAccess> findBySchoolIdAndChildIdAndStatus(UUID schoolId, UUID childId, SchoolChildAccessStatus status);

    List<SchoolChildAccess> findByChildIdAndStatusOrderByGrantedAtAsc(UUID childId, SchoolChildAccessStatus status);

    long countBySchoolIdAndStatus(UUID schoolId, SchoolChildAccessStatus status);

    List<SchoolChildAccess> findByChildIdOrderByGrantedAtAsc(UUID childId);
}
