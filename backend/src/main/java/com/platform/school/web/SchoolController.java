package com.platform.school.web;

import com.platform.identity.entity.PlatformRole;
import com.platform.school.entity.*;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.service.*;
import com.platform.learning.web.EnrollmentDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Schools and classes over HTTP under /api/v1/schools. Who may act is checked by SchoolAccessGuard and the
 * services, never by role alone.
 */
@RestController @RequestMapping("/api/v1/schools")
public class SchoolController {
 private final SchoolService service; private final SchoolStaffService staffService; private final SchoolAccessGuard guard; private final SchoolRepository schools; private final SchoolLearningService learning;
 public SchoolController(SchoolService service,SchoolStaffService staffService,SchoolAccessGuard guard,SchoolRepository schools,SchoolLearningService learning){this.service=service;this.staffService=staffService;this.guard=guard;this.schools=schools;this.learning=learning;}
 /** A school is created by its own principal, who becomes its principal (TD-23: an administrator used to be recorded as one). */
 @PostMapping public ResponseEntity<SchoolResponse> create(@Valid @RequestBody CreateSchool request,@AuthenticationPrincipal UUID accountId){ var account=guard.requireStaff(accountId); if(account.getPlatformRole()!=PlatformRole.PRINCIPAL) throw new org.springframework.security.access.AccessDeniedException("A school is created by its principal"); var setUp=service.setUpSchool(request.name(),accountId); return ResponseEntity.status(setUp.created()?HttpStatus.CREATED:HttpStatus.OK).body(SchoolResponse.of(setUp.school())); }
 @GetMapping("/mine") public List<SchoolResponse> mine(@AuthenticationPrincipal UUID accountId){var a=guard.requireStaff(accountId); return (a.getPlatformRole()==PlatformRole.ADMIN?schools.findAll():schools.findByPrincipalAccountId(accountId)).stream().map(SchoolResponse::of).toList();}
 @PostMapping("/{schoolId}/classes") public ResponseEntity<ClassResponse> createClass(@PathVariable UUID schoolId,@Valid @RequestBody CreateClass request,@AuthenticationPrincipal UUID accountId){guard.requireSchoolManage(accountId,schoolId);return ResponseEntity.status(HttpStatus.CREATED).body(ClassResponse.of(service.createClass(schoolId,request.name())));}
 /** Only a school's own ACTIVE staff can be assigned to its classes (SchoolStaffService#assign). */
 @PostMapping("/classes/{classId}/teachers") public ResponseEntity<AssignmentResponse> assignTeacher(@PathVariable UUID classId,@Valid @RequestBody AssignTeacher request,@AuthenticationPrincipal UUID accountId){var c=guard.requireClassAccess(accountId,classId);guard.requireSchoolManage(accountId,c.getSchoolId());return ResponseEntity.status(HttpStatus.CREATED).body(AssignmentResponse.of(staffService.assign(classId,request.teacherAccountId(),accountId)));}
 @PostMapping("/classes/{classId}/children") public ResponseEntity<EnrollmentResponse> enrollChild(@PathVariable UUID classId,@Valid @RequestBody EnrollChild request,@AuthenticationPrincipal UUID accountId){guard.requireClassAccess(accountId,classId);return ResponseEntity.status(HttpStatus.CREATED).body(EnrollmentResponse.of(service.enrollChild(classId,request.childId())));}
 @GetMapping("/classes/mine") public List<ClassResponse> myClasses(@AuthenticationPrincipal UUID accountId){var a=guard.requireStaff(accountId); if(a.getPlatformRole()==PlatformRole.TEACHER)return service.classesForTeacher(accountId).stream().map(ClassResponse::of).toList(); return (a.getPlatformRole()==PlatformRole.ADMIN?schools.findAll():schools.findByPrincipalAccountId(accountId)).stream().flatMap(s->service.classesForSchool(s.getId()).stream()).map(ClassResponse::of).toList();}

 /** The class's staff assign a curriculum: every member starts it, and so will children who join later (PD-01). */
 @PostMapping("/classes/{classId}/curricula/{curriculumId}") public List<EnrollmentDtos.EnrollmentResponse> assignCurriculum(@PathVariable UUID classId,@PathVariable UUID curriculumId,@AuthenticationPrincipal UUID accountId){guard.requireClassStaff(accountId,classId);return service.assignCurriculum(classId,curriculumId,accountId).stream().map(EnrollmentDtos.EnrollmentResponse::from).toList();}
 @DeleteMapping("/classes/{classId}/curricula/{curriculumId}") public ResponseEntity<Void> unassignCurriculum(@PathVariable UUID classId,@PathVariable UUID curriculumId,@AuthenticationPrincipal UUID accountId){guard.requireClassStaff(accountId,classId);service.unassignCurriculum(classId,curriculumId,accountId);return ResponseEntity.noContent().build();}
 @GetMapping("/classes/{classId}/curricula") public List<SchoolLearningService.ClassCurriculum> classCurricula(@PathVariable UUID classId,@AuthenticationPrincipal UUID accountId){return learning.assigned(accountId,classId);}
 /** The class's members: children whose family gave the school access (PD-01). Its staff only. */
 @GetMapping("/classes/{classId}/children") public List<SchoolLearningService.Member> classChildren(@PathVariable UUID classId,@AuthenticationPrincipal UUID accountId){return learning.roster(accountId,classId);}
 public record CreateSchool(@NotBlank @jakarta.validation.constraints.Size(max = 200) String name){} public record CreateClass(@NotBlank String name){} public record AssignTeacher(@NotNull UUID teacherAccountId){} public record EnrollChild(@NotNull UUID childId){}
 public record SchoolResponse(UUID id,String name,UUID principalAccountId,boolean archived){static SchoolResponse of(School x){return new SchoolResponse(x.getId(),x.getName(),x.getPrincipalAccountId(),x.isArchived());}}
 public record ClassResponse(UUID id,UUID schoolId,String name,boolean archived,UUID academicYearId){static ClassResponse of(SchoolClass x){return new ClassResponse(x.getId(),x.getSchoolId(),x.getName(),x.isArchived(),x.getAcademicYearId());}}
 public record AssignmentResponse(UUID id,UUID schoolClassId,UUID teacherAccountId){static AssignmentResponse of(TeacherAssignment x){return new AssignmentResponse(x.getId(),x.getSchoolClassId(),x.getTeacherAccountId());}}
 public record EnrollmentResponse(UUID id,UUID schoolClassId,UUID childId){static EnrollmentResponse of(ClassEnrollment x){return new EnrollmentResponse(x.getId(),x.getSchoolClassId(),x.getChildId());}}
}
