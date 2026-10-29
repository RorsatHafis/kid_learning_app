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

    /** M12 B2: children with access in any of the schools, each counted once (a child in two branches is one child). */
    @org.springframework.data.jpa.repository.Query("select count(distinct a.childId) from SchoolChildAccess a"
            + " where a.schoolId in :schoolIds and a.status = :status")
    long countDistinctChildren(@org.springframework.data.repository.query.Param("schoolIds") java.util.Collection<UUID> schoolIds,
                               @org.springframework.data.repository.query.Param("status") SchoolChildAccessStatus status);

    List<SchoolChildAccess> findByChildIdOrderByGrantedAtAsc(UUID childId);
}
