package com.platform.school.repository;

import com.platform.school.entity.AcademicYear;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    List<AcademicYear> findBySchoolIdOrderByStartsOnDesc(UUID schoolId);

    /** Every change to a school's years (dates, current, terms) takes this lock first: overlaps can't race in. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select y from AcademicYear y where y.schoolId = :schoolId order by y.startsOn")
    List<AcademicYear> lockBySchoolId(@Param("schoolId") UUID schoolId);
}
