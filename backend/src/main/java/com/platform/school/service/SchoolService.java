package com.platform.school.service;

import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.*;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.*;
import com.platform.school.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SchoolService {
    private final SchoolRepository schools; private final SchoolClassRepository classes; private final TeacherAssignmentRepository assignments; private final ClassEnrollmentRepository enrollments; private final AccountRepository accounts; private final ChildRepository children;
    public SchoolService(SchoolRepository schools, SchoolClassRepository classes, TeacherAssignmentRepository assignments, ClassEnrollmentRepository enrollments, AccountRepository accounts, ChildRepository children) { this.schools=schools;this.classes=classes;this.assignments=assignments;this.enrollments=enrollments;this.accounts=accounts;this.children=children; }
    @Transactional public School createSchool(String name, UUID principalAccountId) { return schools.save(School.create(name, principalAccountId)); }
    @Transactional public SchoolClass createClass(UUID schoolId,String name){ if(!schools.existsById(schoolId)) throw new ResourceNotFoundException("School",schoolId); return classes.save(SchoolClass.create(schoolId,name)); }
    @Transactional public TeacherAssignment assignTeacher(UUID classId,UUID teacherId){ if(!classes.existsById(classId))throw new ResourceNotFoundException("SchoolClass",classId); Account teacher=accounts.findById(teacherId).orElseThrow(()->new ResourceNotFoundException("Account",teacherId)); if(teacher.getPlatformRole()!=PlatformRole.TEACHER)throw new IllegalArgumentException("Assigned account must have TEACHER role"); return assignments.save(TeacherAssignment.assign(classId,teacherId)); }
    @Transactional public ClassEnrollment enrollChild(UUID classId,UUID childId){ if(!classes.existsById(classId))throw new ResourceNotFoundException("SchoolClass",classId); if(!children.existsById(childId))throw new ResourceNotFoundException("Child",childId); return enrollments.save(ClassEnrollment.enroll(classId,childId)); }
    @Transactional(readOnly=true) public List<SchoolClass> classesForTeacher(UUID teacherId){ return assignments.findByTeacherAccountIdAndStatus(teacherId,TeacherAssignmentStatus.ACTIVE).stream().map(a->classes.findById(a.getSchoolClassId()).orElse(null)).filter(Objects::nonNull).toList(); }
    @Transactional(readOnly=true) public List<SchoolClass> classesForSchool(UUID schoolId){ return classes.findBySchoolId(schoolId); }
    @Transactional(readOnly=true) public List<ClassEnrollment> activeEnrollments(UUID classId){return enrollments.findBySchoolClassIdAndStatus(classId,ClassEnrollmentStatus.ACTIVE);}
}
