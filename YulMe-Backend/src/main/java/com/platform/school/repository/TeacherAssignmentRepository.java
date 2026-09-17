package com.platform.school.repository;
import com.platform.school.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, UUID> { List<TeacherAssignment> findByTeacherAccountIdAndStatus(UUID teacherAccountId, TeacherAssignmentStatus status); boolean existsBySchoolClassIdAndTeacherAccountIdAndStatus(UUID schoolClassId, UUID teacherAccountId, TeacherAssignmentStatus status); }
