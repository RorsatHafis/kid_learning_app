package com.platform.school.web;

import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.*;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.service.*;
import com.platform.learning.service.EnrollmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/schools")
public class SchoolController {
 private final SchoolService service; private final SchoolAccessGuard guard; private final EnrollmentService enrollmentService; private final SchoolRepository schools; private final ChildRepository children; private final AccountRepository accounts;
 public SchoolController(SchoolService service,SchoolAccessGuard guard,SchoolRepository schools,ChildRepository children,AccountRepository accounts,EnrollmentService enrollmentService){this.service=service;this.guard=guard;this.schools=schools;this.children=children;this.accounts=accounts;this.enrollmentService=enrollmentService;}
 @PostMapping public ResponseEntity<SchoolResponse> create(@Valid @RequestBody CreateSchool request,@AuthenticationPrincipal UUID accountId){ var account=guard.requireStaff(accountId); if(account.getPlatformRole()!=PlatformRole.PRINCIPAL&&account.getPlatformRole()!=PlatformRole.ADMIN) throw new org.springframework.security.access.AccessDeniedException("Only principal or admin may create a school"); return ResponseEntity.status(HttpStatus.CREATED).body(SchoolResponse.of(service.createSchool(request.name(),accountId))); }
 @GetMapping("/mine") public List<SchoolResponse> mine(@AuthenticationPrincipal UUID accountId){var a=guard.requireStaff(accountId); return (a.getPlatformRole()==PlatformRole.ADMIN?schools.findAll():schools.findByPrincipalAccountId(accountId)).stream().map(SchoolResponse::of).toList();}
 @PostMapping("/{schoolId}/classes") public ResponseEntity<ClassResponse> createClass(@PathVariable UUID schoolId,@Valid @RequestBody CreateClass request,@AuthenticationPrincipal UUID accountId){guard.requireSchoolManage(accountId,schoolId);return ResponseEntity.status(HttpStatus.CREATED).body(ClassResponse.of(service.createClass(schoolId,request.name())));}
 @PostMapping("/classes/{classId}/teachers") public ResponseEntity<AssignmentResponse> assignTeacher(@PathVariable UUID classId,@Valid @RequestBody AssignTeacher request,@AuthenticationPrincipal UUID accountId){var c=guard.requireClassAccess(accountId,classId);guard.requireSchoolManage(accountId,c.getSchoolId());return ResponseEntity.status(HttpStatus.CREATED).body(AssignmentResponse.of(service.assignTeacher(classId,request.teacherAccountId())));}
 @PostMapping("/classes/{classId}/children") public ResponseEntity<EnrollmentResponse> enrollChild(@PathVariable UUID classId,@Valid @RequestBody EnrollChild request,@AuthenticationPrincipal UUID accountId){guard.requireClassAccess(accountId,classId);return ResponseEntity.status(HttpStatus.CREATED).body(EnrollmentResponse.of(service.enrollChild(classId,request.childId())));}
 @GetMapping("/classes/mine") public List<ClassResponse> myClasses(@AuthenticationPrincipal UUID accountId){var a=guard.requireStaff(accountId); if(a.getPlatformRole()==PlatformRole.TEACHER)return service.classesForTeacher(accountId).stream().map(ClassResponse::of).toList(); return (a.getPlatformRole()==PlatformRole.ADMIN?schools.findAll():schools.findByPrincipalAccountId(accountId)).stream().flatMap(s->service.classesForSchool(s.getId()).stream()).map(ClassResponse::of).toList();}

 @PostMapping("/classes/{classId}/curricula/{curriculumId}") public List<EnrollmentResponse> assignCurriculum(@PathVariable UUID classId,@PathVariable UUID curriculumId,@AuthenticationPrincipal UUID accountId){guard.requireClassAccess(accountId,classId);return service.activeEnrollments(classId).stream().map(e->{enrollmentService.enroll(e.getChildId(),curriculumId);return EnrollmentResponse.of(e);}).toList();}
 @GetMapping("/classes/{classId}/children") public List<ChildResponse> classChildren(@PathVariable UUID classId,@AuthenticationPrincipal UUID accountId){guard.requireClassAccess(accountId,classId);return service.activeEnrollments(classId).stream().map(e->children.findById(e.getChildId()).orElseThrow()).map(ChildResponse::of).toList();}
 public record CreateSchool(@NotBlank String name){} public record CreateClass(@NotBlank String name){} public record AssignTeacher(@NotNull UUID teacherAccountId){} public record EnrollChild(@NotNull UUID childId){}
 public record SchoolResponse(UUID id,String name,UUID principalAccountId){static SchoolResponse of(School x){return new SchoolResponse(x.getId(),x.getName(),x.getPrincipalAccountId());}}
 public record ClassResponse(UUID id,UUID schoolId,String name){static ClassResponse of(SchoolClass x){return new ClassResponse(x.getId(),x.getSchoolId(),x.getName());}}
 public record AssignmentResponse(UUID id,UUID schoolClassId,UUID teacherAccountId){static AssignmentResponse of(TeacherAssignment x){return new AssignmentResponse(x.getId(),x.getSchoolClassId(),x.getTeacherAccountId());}}
 public record EnrollmentResponse(UUID id,UUID schoolClassId,UUID childId){static EnrollmentResponse of(ClassEnrollment x){return new EnrollmentResponse(x.getId(),x.getSchoolClassId(),x.getChildId());}}
 public record ChildResponse(UUID id,String displayName){static ChildResponse of(Child x){return new ChildResponse(x.getId(),x.getDisplayName());}}
}
