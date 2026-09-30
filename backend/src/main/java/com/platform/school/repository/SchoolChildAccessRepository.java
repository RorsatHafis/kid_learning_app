package com.platform.school.repository;

import com.platform.school.entity.SchoolChildAccess;
import com.platform.school.entity.SchoolChildAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolChildAccessRepository extends JpaRepository<SchoolChildAccess, UUID> {

    Optional<SchoolChildAccess> findBySchoolIdAndChildIdAndStatus(UUID schoolId, UUID childId, SchoolChildAccessStatus status);

    List<SchoolChildAccess> findByChildIdAndStatusOrderByGrantedAtAsc(UUID childId, SchoolChildAccessStatus status);

    long countBySchoolIdAndStatus(UUID schoolId, SchoolChildAccessStatus status);
}
