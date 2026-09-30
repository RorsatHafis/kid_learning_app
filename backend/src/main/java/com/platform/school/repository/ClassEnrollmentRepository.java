package com.platform.school.repository;

import com.platform.school.entity.ClassEnrollment;
import com.platform.school.entity.ClassEnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, UUID> {

    List<ClassEnrollment> findByChildIdAndStatus(UUID childId, ClassEnrollmentStatus status);

    List<ClassEnrollment> findBySchoolClassIdAndStatus(UUID schoolClassId, ClassEnrollmentStatus status);

    boolean existsBySchoolClassIdAndChildIdAndStatus(UUID schoolClassId, UUID childId, ClassEnrollmentStatus status);

    Optional<ClassEnrollment> findBySchoolClassIdAndChildId(UUID schoolClassId, UUID childId);

    /*
     * Membership that counts (PD-01): an ACTIVE class enrolment backed by the family's ACTIVE grant to
     * the class's school. Rows without a grant (TD-14, before PD-01) are never members.
     */

    @Query("""
            select e from ClassEnrollment e, SchoolClass c, SchoolChildAccess g
            where e.schoolClassId = :classId and e.status = com.platform.school.entity.ClassEnrollmentStatus.ACTIVE
              and c.id = e.schoolClassId
              and g.schoolId = c.schoolId and g.childId = e.childId
              and g.status = com.platform.school.entity.SchoolChildAccessStatus.ACTIVE
            order by e.createdAt, e.id
            """)
    List<ClassEnrollment> findMembers(@Param("classId") UUID classId);

    @Query("""
            select e from ClassEnrollment e, SchoolClass c, SchoolChildAccess g
            where e.childId = :childId and e.status = com.platform.school.entity.ClassEnrollmentStatus.ACTIVE
              and c.id = e.schoolClassId
              and g.schoolId = c.schoolId and g.childId = e.childId
              and g.status = com.platform.school.entity.SchoolChildAccessStatus.ACTIVE
            """)
    List<ClassEnrollment> findMembershipsOf(@Param("childId") UUID childId);

    /** The child's ACTIVE enrolments in any class of a school, backed or not: all end with the grant. */
    @Query("""
            select e from ClassEnrollment e, SchoolClass c
            where e.childId = :childId and e.status = com.platform.school.entity.ClassEnrollmentStatus.ACTIVE
              and c.id = e.schoolClassId and c.schoolId = :schoolId
            """)
    List<ClassEnrollment> findActiveInSchool(@Param("childId") UUID childId, @Param("schoolId") UUID schoolId);
}
