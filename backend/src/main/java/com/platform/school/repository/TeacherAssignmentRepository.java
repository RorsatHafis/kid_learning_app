package com.platform.school.repository;
import com.platform.school.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
/**
 * Persistence for teacher assignments: a teacher's classes and a class's teachers.
 */
public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, UUID> { List<TeacherAssignment> findByTeacherAccountIdAndStatus(UUID teacherAccountId, TeacherAssignmentStatus status); boolean existsBySchoolClassIdAndTeacherAccountIdAndStatus(UUID schoolClassId, UUID teacherAccountId, TeacherAssignmentStatus status); Optional<TeacherAssignment> findBySchoolClassIdAndTeacherAccountId(UUID schoolClassId, UUID teacherAccountId); List<TeacherAssignment> findBySchoolClassIdAndStatus(UUID schoolClassId, TeacherAssignmentStatus status); }
