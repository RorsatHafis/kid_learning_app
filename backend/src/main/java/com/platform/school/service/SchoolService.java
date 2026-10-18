package com.platform.school.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
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
    private final SchoolRepository schools; private final SchoolClassRepository classes; private final TeacherAssignmentRepository assignments; private final ClassEnrollmentRepository enrollments; private final AccountRepository accounts; private final ChildRepository children; private final CurriculumRepository curricula; private final CurriculumService curriculumService; private final EnrollmentService enrollmentService; private final ClassCurriculumAssignmentRepository classCurricula; private final AuditService audit; private final com.platform.subscription.service.SchoolEntitlementService entitlements;
    public SchoolService(SchoolRepository schools, SchoolClassRepository classes, TeacherAssignmentRepository assignments, ClassEnrollmentRepository enrollments, AccountRepository accounts, ChildRepository children, CurriculumRepository curricula, CurriculumService curriculumService, EnrollmentService enrollmentService, ClassCurriculumAssignmentRepository classCurricula, AuditService audit, com.platform.subscription.service.SchoolEntitlementService entitlements) { this.entitlements=entitlements; this.schools=schools;this.classes=classes;this.assignments=assignments;this.enrollments=enrollments;this.accounts=accounts;this.children=children;this.curricula=curricula;this.curriculumService=curriculumService;this.enrollmentService=enrollmentService;this.classCurricula=classCurricula;this.audit=audit; }
    static final String ARCHIVED_CLASS="This class is archived: restore it before adding to it.";
    @Transactional public School createSchool(String name, UUID principalAccountId) { return schools.save(School.create(name, principalAccountId)); }
    @Transactional public SchoolClass createClass(UUID schoolId,String name){ School school=schools.findById(schoolId).orElseThrow(()->new ResourceNotFoundException("School",schoolId)); if(school.isArchived()) throw new IllegalStateException("This school is archived: restore it to add classes."); entitlements.requireRoomForClass(schoolId); return classes.save(SchoolClass.create(schoolId,name)); }
    /**
     * A family's child joins a class only with that family's authorization (CLAUDE.md §10, §15).
     * The family grants it with the class's code (PD-01, SchoolJoinService#join); no staff
     * member - teacher, principal or admin - can put a child in a class: knowing a child's id is
     * not permission. Refused before any lookup, so the answer never reveals whether the child
     * exists.
     */
    public ClassEnrollment enrollChild(UUID classId,UUID childId){ throw new AccessDeniedException("A child joins a class when their family enters the class's code"); }
    /**
     * The class assigns a curriculum the assigning account may hand out: one with a published
     * version that is platform content or the account's own (TD-12) - never another teacher's
     * private curriculum. Anything else answers 404, as if it did not exist. The assignment is
     * recorded (it is what the school may see, and what a child joining later starts), and every
     * member (a child whose family granted access, PD-01) is enrolled. One transaction, so a
     * failure part-way leaves no child half-assigned.
     */
    @Transactional public List<Enrollment> assignCurriculum(UUID classId,UUID curriculumId,UUID accountId){
        // X17 (PD-25): an archived class takes nothing new.
        if(classes.findById(classId).map(SchoolClass::isArchived).orElse(false)) throw new IllegalStateException(ARCHIVED_CLASS);
        Curriculum curriculum=curricula.findById(curriculumId).filter(c->c.getOwnerAccountId()==null||c.getOwnerAccountId().equals(accountId)).filter(c->curriculumService.hasPublishedVersion(c.getId())).orElseThrow(()->new ResourceNotFoundException("Curriculum",curriculumId));
        // X14: an archived curriculum is not assigned anew; classes already assigning it keep it.
        if(curriculum.getStatus()==com.platform.common.entity.CatalogStatus.ARCHIVED&&classCurricula.findBySchoolClassIdAndCurriculumId(classId,curriculumId).filter(a->a.getStatus()==ClassCurriculumAssignmentStatus.ACTIVE).isEmpty()) throw new IllegalStateException("This curriculum is archived and no longer offered.");
        ClassCurriculumAssignment assignment=classCurricula.findBySchoolClassIdAndCurriculumId(classId,curriculumId).map(existing->{existing.reassign(accountId);return existing;}).orElseGet(()->ClassCurriculumAssignment.assign(classId,curriculumId,accountId));
        classCurricula.saveAndFlush(assignment);
        record(accountId,"CLASS_CURRICULUM_ASSIGNED",classId,Map.of("curriculumId",curriculumId));
        return members(classId).stream().map(e->enrollmentService.enroll(e.getChildId(),curriculum.getId())).toList();
    }
    /**
     * The class stops assigning a curriculum: the school no longer sees it, and children joining
     * later don't start it. Members keep their journeys in it; those are the children's now.
     */
    @Transactional public void unassignCurriculum(UUID classId,UUID curriculumId,UUID accountId){
        ClassCurriculumAssignment assignment=classCurricula.findBySchoolClassIdAndCurriculumId(classId,curriculumId).filter(a->a.getStatus()==ClassCurriculumAssignmentStatus.ACTIVE).orElseThrow(()->new ResourceNotFoundException("Curriculum",curriculumId));
        assignment.remove();
        record(accountId,"CLASS_CURRICULUM_UNASSIGNED",classId,Map.of("curriculumId",curriculumId));
    }
    @Transactional(readOnly=true) public List<ClassCurriculumAssignment> assignedCurricula(UUID classId){ return classCurricula.findBySchoolClassIdAndStatusOrderByCreatedAtAsc(classId,ClassCurriculumAssignmentStatus.ACTIVE); }
    private void record(UUID actorId,String action,UUID classId,Map<String,Object> metadata){ Account actor=accounts.findById(actorId).orElseThrow(); audit.record(new RecordAuditEvent(null,actorId,AuditActorType.valueOf(actor.getPlatformRole().name()),action,"SCHOOL_CLASS",classId,metadata)); }
    @Transactional(readOnly=true) public List<SchoolClass> classesForTeacher(UUID teacherId){ return assignments.findByTeacherAccountIdAndStatus(teacherId,TeacherAssignmentStatus.ACTIVE).stream().map(a->classes.findById(a.getSchoolClassId()).orElse(null)).filter(Objects::nonNull).toList(); }
    @Transactional(readOnly=true) public List<SchoolClass> classesForSchool(UUID schoolId){ return classes.findBySchoolId(schoolId); }
    /** The class's members: children in it whose family's grant to the school is ACTIVE (PD-01). */
    @Transactional(readOnly=true) public List<ClassEnrollment> members(UUID classId){return enrollments.findMembers(classId);}
}
