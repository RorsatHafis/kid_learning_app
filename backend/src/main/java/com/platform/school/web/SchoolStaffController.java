package com.platform.school.web;

import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.school.entity.SchoolClass;
import com.platform.school.entity.SchoolStaffStatus;
import com.platform.school.service.SchoolAccessGuard;
import com.platform.school.service.SchoolService;
import com.platform.school.service.SchoolStaffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A school's staff and class assignments (see {@link SchoolStaffService}). The school side
 * requires managing the school (its principal, or an admin); the teacher side acts only on
 * the signed-in teacher's own invitations.
 */
@RestController
@RequestMapping("/api/v1/schools")
public class SchoolStaffController {

    private final SchoolStaffService staffService;
    private final SchoolService schoolService;
    private final SchoolAccessGuard guard;

    public SchoolStaffController(SchoolStaffService staffService, SchoolService schoolService, SchoolAccessGuard guard) {
        this.staffService = staffService;
        this.schoolService = schoolService;
        this.guard = guard;
    }

    public record InviteTeacher(@NotBlank @Email @Size(max = 320) String email) {
    }

    public record StaffResponse(UUID id, UUID accountId, String email, SchoolStaffStatus status,
                                Instant invitedAt, Instant respondedAt) {
        static StaffResponse of(SchoolStaffService.StaffMember member) {
            return new StaffResponse(member.membership().getId(), member.account().getId(), member.account().getEmail(),
                    member.membership().getStatus(), member.membership().getInvitedAt(),
                    member.membership().getRespondedAt());
        }
    }

    public record TeacherSummary(UUID accountId, String email) {
        static TeacherSummary of(Account account) {
            return new TeacherSummary(account.getId(), account.getEmail());
        }
    }

    public record ClassWithTeachers(UUID id, UUID schoolId, String name, List<TeacherSummary> teachers, boolean archived) {
    }

    public record InvitationResponse(UUID id, UUID schoolId, String schoolName, Instant invitedAt) {
        static InvitationResponse of(SchoolStaffService.Invitation invitation) {
            return new InvitationResponse(invitation.membership().getId(), invitation.school().getId(),
                    invitation.school().getName(), invitation.membership().getInvitedAt());
        }
    }

    public record MembershipResponse(UUID id, UUID schoolId, String schoolName, Instant joinedAt) {
        static MembershipResponse of(SchoolStaffService.Invitation membership) {
            return new MembershipResponse(membership.membership().getId(), membership.school().getId(),
                    membership.school().getName(), membership.membership().getRespondedAt());
        }
    }

    // ------------------------------------------------------------------ the school's side

    @GetMapping("/{schoolId}/staff")
    public List<StaffResponse> staff(@PathVariable UUID schoolId, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        return staffService.list(schoolId).stream().map(StaffResponse::of).toList();
    }

    @PostMapping("/{schoolId}/staff")
    public ResponseEntity<StaffResponse> invite(@PathVariable UUID schoolId, @Valid @RequestBody InviteTeacher request,
                                                @AuthenticationPrincipal UUID accountId, HttpServletRequest http) {
        guard.requireSchoolManage(accountId, schoolId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StaffResponse.of(staffService.invite(schoolId, request.email(), accountId, http.getRemoteAddr())));
    }

    @DeleteMapping("/{schoolId}/staff/{membershipId}")
    public ResponseEntity<Void> remove(@PathVariable UUID schoolId, @PathVariable UUID membershipId,
                                       @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        staffService.remove(schoolId, membershipId, accountId);
        return ResponseEntity.noContent().build();
    }

    /** The school's classes, each with its assigned teachers. */
    @GetMapping("/{schoolId}/classes")
    public List<ClassWithTeachers> classes(@PathVariable UUID schoolId, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        return schoolService.classesForSchool(schoolId).stream().map(this::withTeachers).toList();
    }

    /** One class with its teachers, for whoever may see the class: its principal, its teachers, an admin. */
    @GetMapping("/classes/{classId}")
    public ClassWithTeachers schoolClass(@PathVariable UUID classId, @AuthenticationPrincipal UUID accountId) {
        return withTeachers(guard.requireClassAccess(accountId, classId));
    }

    @DeleteMapping("/classes/{classId}/teachers/{teacherAccountId}")
    public ResponseEntity<Void> unassign(@PathVariable UUID classId, @PathVariable UUID teacherAccountId,
                                         @AuthenticationPrincipal UUID accountId) {
        SchoolClass schoolClass = guard.requireClassAccess(accountId, classId);
        guard.requireSchoolManage(accountId, schoolClass.getSchoolId());
        staffService.unassign(classId, teacherAccountId, accountId);
        return ResponseEntity.noContent().build();
    }

    private ClassWithTeachers withTeachers(SchoolClass schoolClass) {
        return new ClassWithTeachers(schoolClass.getId(), schoolClass.getSchoolId(), schoolClass.getName(),
                staffService.teachersOf(schoolClass.getId()).stream().map(TeacherSummary::of).toList(), schoolClass.isArchived());
    }

    // ------------------------------------------------------------------ the teacher's side

    @GetMapping("/invitations/mine")
    public List<InvitationResponse> myInvitations(@AuthenticationPrincipal UUID accountId) {
        requireTeacher(accountId);
        return staffService.openInvitations(accountId).stream().map(InvitationResponse::of).toList();
    }

    /** Y21: the schools whose staff the teacher is on, so they can see them and leave one. */
    @GetMapping("/staff/mine")
    public List<MembershipResponse> myMemberships(@AuthenticationPrincipal UUID accountId) {
        requireTeacher(accountId);
        return staffService.memberships(accountId).stream().map(MembershipResponse::of).toList();
    }

    @PostMapping("/staff/mine/{membershipId}/leave")
    public ResponseEntity<Void> leave(@PathVariable UUID membershipId, @AuthenticationPrincipal UUID accountId) {
        requireTeacher(accountId);
        staffService.leave(membershipId, accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/{membershipId}/accept")
    public ResponseEntity<Void> accept(@PathVariable UUID membershipId, @AuthenticationPrincipal UUID accountId) {
        requireTeacher(accountId);
        staffService.respond(membershipId, accountId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/{membershipId}/decline")
    public ResponseEntity<Void> decline(@PathVariable UUID membershipId, @AuthenticationPrincipal UUID accountId) {
        requireTeacher(accountId);
        staffService.respond(membershipId, accountId, false);
        return ResponseEntity.noContent().build();
    }

    private void requireTeacher(UUID accountId) {
        if (guard.requireStaff(accountId).getPlatformRole() != PlatformRole.TEACHER) {
            throw new AccessDeniedException("Only teachers are on a school's teaching staff");
        }
    }

}
