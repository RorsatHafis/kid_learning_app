package com.platform.school.repository;
import com.platform.school.entity.SchoolClass; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
/**
 * Persistence for a school's classes and their count against its plan.
 */
public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    List<SchoolClass> findBySchoolId(UUID schoolId);

    long countBySchoolIdAndStatus(UUID schoolId, com.platform.common.entity.CatalogStatus status);
}
