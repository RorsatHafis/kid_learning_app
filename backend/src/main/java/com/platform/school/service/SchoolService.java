package com.platform.school.service;

import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.identity.entity.*;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.*;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.service.EnrollmentService;
import com.platform.school.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SchoolService {
    private final SchoolRepository schools; private final SchoolClassRepository classes; private final TeacherAssignmentRepository assignments; private final ClassEnrollmentRepository enrollments; private final AccountRepository accounts; private final ChildRepository children; private final CurriculumRepository curricula; private final CurriculumService curriculumService; private final EnrollmentService enrollmentService;
    public SchoolService(SchoolRepository schools, SchoolClassRepository classes, TeacherAssignmentRepository assignments, ClassEnrollmentRepository enrollments, AccountRepository accounts, ChildRepository children, CurriculumRepository curricula, CurriculumService curriculumService, EnrollmentService enrollmentService) { this.schools=schools;this.classes=classes;this.assignments=assignments;this.enrollments=enrollments;this.accounts=accounts;this.children=children;this.curricula=curricula;this.curriculumService=curriculumService;this.enrollmentService=enrollmentService; }
    @Transactional public School createSchool(String name, UUID principalAccountId) { return schools.save(School.create(name, principalAccountId)); }
    @Transactional public SchoolClass createClass(UUID schoolId,String name){ if(!schools.existsById(schoolId)) throw new ResourceNotFoundException("School",schoolId); return classes.save(SchoolClass.create(schoolId,name)); }
    @Transactional public TeacherAssignment assignTeacher(UUID classId,UUID teacherId){ if(!classes.existsById(classId))throw new ResourceNotFoundException("SchoolClass",classId); Account teacher=accounts.findById(teacherId).orElseThrow(()->new ResourceNotFoundException("Account",teacherId)); if(teacher.getPlatformRole()!=PlatformRole.TEACHER)throw new IllegalArgumentException("Assigned account must have TEACHER role"); return assignments.save(TeacherAssignment.assign(classId,teacherId)); }
    /**
     * A family's child joins a class only with that family's authorization (CLAUDE.md §10, §15;
     * directive §15). Nothing in the domain records such an authorization yet (PD-01), so no
     * staff member - teacher, principal or admin - can enroll a child: knowing a child's id is
     * not permission. Refused before any lookup, so the answer never reveals whether the child
     * exists.
     */
    public ClassEnrollment enrollChild(UUID classId,UUID childId){ throw new AccessDeniedException("Enrolling a child in a class requires the family's authorization, which the platform does not support yet"); }
    /**
     * Enrolls every active class member in a curriculum the assigning account may hand out:
     * one with a published version that is platform content or the account's own (TD-12) -
     * never another teacher's private curriculum. Anything else answers 404, as if it did not
     * exist. One transaction, so a failure part-way leaves no child half-assigned.
     */
    @Transactional public List<Enrollment> assignCurriculum(UUID classId,UUID curriculumId,UUID accountId){ Curriculum curriculum=curricula.findById(curriculumId).filter(c->c.getOwnerAccountId()==null||c.getOwnerAccountId().equals(accountId)).filter(c->curriculumService.hasPublishedVersion(c.getId())).orElseThrow(()->new ResourceNotFoundException("Curriculum",curriculumId)); return activeEnrollments(classId).stream().map(e->enrollmentService.enroll(e.getChildId(),curriculum.getId())).toList(); }
    @Transactional(readOnly=true) public List<SchoolClass> classesForTeacher(UUID teacherId){ return assignments.findByTeacherAccountIdAndStatus(teacherId,TeacherAssignmentStatus.ACTIVE).stream().map(a->classes.findById(a.getSchoolClassId()).orElse(null)).filter(Objects::nonNull).toList(); }
    @Transactional(readOnly=true) public List<SchoolClass> classesForSchool(UUID schoolId){ return classes.findBySchoolId(schoolId); }
    @Transactional(readOnly=true) public List<ClassEnrollment> activeEnrollments(UUID classId){return enrollments.findBySchoolClassIdAndStatus(classId,ClassEnrollmentStatus.ACTIVE);}
}
