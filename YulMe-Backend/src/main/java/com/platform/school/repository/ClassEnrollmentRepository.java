package com.platform.school.repository;
import com.platform.school.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, UUID> { List<ClassEnrollment> findByChildIdAndStatus(UUID childId, ClassEnrollmentStatus status); List<ClassEnrollment> findBySchoolClassIdAndStatus(UUID schoolClassId, ClassEnrollmentStatus status); boolean existsBySchoolClassIdAndChildIdAndStatus(UUID schoolClassId, UUID childId, ClassEnrollmentStatus status); }
