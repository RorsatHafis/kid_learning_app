package com.platform.school.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.identity.service.AccountRecoveryService;
import com.platform.notification.EmailMessage;
import com.platform.notification.PlatformMailProperties;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolClass;
import com.platform.school.entity.SchoolStaff;
import com.platform.school.entity.SchoolStaffStatus;
import com.platform.school.entity.TeacherAssignment;
import com.platform.school.entity.TeacherAssignmentStatus;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.repository.SchoolStaffRepository;
import com.platform.school.repository.TeacherAssignmentRepository;
import com.platform.identity.exception.TooManyEmailsException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A school's teachers (CLAUDE.md §15): who works at the school, and which of them teach
 * which class. The two are separate relationships - a principal first invites a teacher to
 * the school, the teacher accepts, and only then can the principal assign them to classes.
 *
 * <p>Invitations are by email. An address with no account gets a new TEACHER account with
 * a random password nobody knows, and an emailed link to choose one (nobody ever sets
 * another person's password). An existing teacher is told to sign in and accept. Other
 * accounts cannot be invited: a role is fixed for an account's lifetime.
 *
 * <p>Removing someone from the staff ends every class assignment they held at that school
 * in the same transaction, so a removed teacher cannot keep a class.
 */
@Service
public class SchoolStaffService {

    static final Duration REINVITE_GAP = Duration.ofSeconds(60);

    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final SchoolStaffRepository staff;
    private final TeacherAssignmentRepository assignments;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AccountRecoveryService recovery;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final PlatformMailProperties mail;
    private final SecureRandom random = new SecureRandom();

    public SchoolStaffService(SchoolRepository schools, SchoolClassRepository classes, SchoolStaffRepository staff,
                              TeacherAssignmentRepository assignments, AccountRepository accounts,
                              PasswordEncoder passwordEncoder, AccountRecoveryService recovery,
                              AuditService auditService, ApplicationEventPublisher events, PlatformMailProperties mail) {
        this.schools = schools;
        this.classes = classes;
        this.staff = staff;
        this.assignments = assignments;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.recovery = recovery;
        this.auditService = auditService;
        this.events = events;
        this.mail = mail;
    }

    public record StaffMember(SchoolStaff membership, Account account) {
    }

    public record Invitation(SchoolStaff membership, School school) {
    }

    // ------------------------------------------------------------------ the school's side

    @Transactional(readOnly = true)
    public List<StaffMember> list(UUID schoolId) {
        return staff.findBySchoolIdOrderByInvitedAtDesc(schoolId).stream()
                .map(m -> new StaffMember(m, accounts.findById(m.getAccountId()).orElseThrow()))
                .toList();
    }

    /**
     * Invites {@code email} to teach at the school. Caller must already be authorized to
     * manage the school. Refused (409) when the address belongs to an account that can't be
     * a teacher or is disabled, or when the person is already on the staff; 429 when the
     * same person was invited within the last minute.
     */
    @Transactional
    public StaffMember invite(UUID schoolId, String email, UUID invitedBy, String requestedIp) {
        School school = schools.findById(schoolId).orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
        Instant now = Instant.now();
        String normalized = Account.normalize(email);
        Account account = accounts.findByNormalizedEmail(normalized).orElse(null);
        boolean newAccount = account == null;
        if (newAccount) {
            byte[] secret = new byte[32];
            random.nextBytes(secret);
            account = accounts.saveAndFlush(Account.register(email.strip(),
                    passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)), PlatformRole.TEACHER));
            audit(invitedBy, "ACCOUNT_CREATED_BY_INVITATION", "ACCOUNT", account.getId(),
                    Map.of("schoolId", schoolId.toString(), "platformRole", PlatformRole.TEACHER.name()));
        } else if (account.getPlatformRole() != PlatformRole.TEACHER || account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "This email address can't be invited as a teacher. Ask them for a different address.");
        } else {
            account = accounts.findForUpdate(account.getId()).orElseThrow();
        }

        SchoolStaff membership = staff.findBySchoolIdAndAccountId(schoolId, account.getId()).orElse(null);
        if (membership == null) {
            membership = staff.save(SchoolStaff.invite(schoolId, account.getId(), invitedBy, now));
        } else {
            if (membership.getStatus() == SchoolStaffStatus.INVITED
                    && membership.getInvitedAt().plus(REINVITE_GAP).isAfter(now)) {
                throw new TooManyEmailsException(Duration.between(now, membership.getInvitedAt().plus(REINVITE_GAP)));
            }
            membership.reinvite(invitedBy, now);
        }

        if (newAccount || recovery.awaitingAccountSetup(account.getId())) {
            recovery.sendAccountSetup(account, new AccountRecoveryService.SetupInvitation(
                    "You're invited to teach at " + school.getName() + " on Bookeyond Kid",
                    school.getName() + " has invited you to join its teachers on Bookeyond Kid.",
                    "After signing in you'll see the invitation and can accept it."), requestedIp);
        } else {
            events.publishEvent(new EmailMessage(account.getEmail(),
                    "You're invited to teach at " + school.getName() + " on Bookeyond Kid", """
                    Hello,

                    %s has invited you to join its teachers on Bookeyond Kid.

                    Sign in at %s/login to accept or decline the invitation.

                    If you weren't expecting this, you can decline it or ignore this email.
                    """.formatted(school.getName(), mail.publicUrl())));
        }
        audit(invitedBy, "SCHOOL_STAFF_INVITED", "SCHOOL", schoolId,
                Map.of("accountId", account.getId().toString(), "newAccount", newAccount));
        return new StaffMember(membership, account);
    }

    /** Ends a membership or withdraws an invitation, and ends every class assignment at this school. */
    @Transactional
    public void remove(UUID schoolId, UUID membershipId, UUID removedBy) {
        SchoolStaff membership = staff.findForUpdate(membershipId)
                .filter(m -> m.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new ResourceNotFoundException("SchoolStaff", membershipId));
        membership.remove(Instant.now());
        int unassigned = endAssignmentsAtSchool(schoolId, membership.getAccountId());
        audit(removedBy, "SCHOOL_STAFF_REMOVED", "SCHOOL", schoolId,
                Map.of("accountId", membership.getAccountId().toString(), "classAssignmentsEnded", unassigned));
    }

    private int endAssignmentsAtSchool(UUID schoolId, UUID teacherId) {
        int ended = 0;
        for (TeacherAssignment assignment : assignments.findByTeacherAccountIdAndStatus(teacherId, TeacherAssignmentStatus.ACTIVE)) {
            SchoolClass schoolClass = classes.findById(assignment.getSchoolClassId()).orElseThrow();
            if (schoolClass.getSchoolId().equals(schoolId)) {
                assignment.remove();
                ended++;
            }
        }
        return ended;
    }

    // ------------------------------------------------------------------ class assignments

    /**
     * Assigns one of the school's own ACTIVE staff to a class of that school. Anyone else -
     * another school's teacher, an invited teacher who hasn't accepted - is refused, and a
     * teacher who is not on this school's staff reads as not found.
     */
    @Transactional
    public TeacherAssignment assign(UUID classId, UUID teacherId, UUID assignedBy) {
        SchoolClass schoolClass = classes.findById(classId).orElseThrow(() -> new ResourceNotFoundException("SchoolClass", classId));
        if (!staff.existsBySchoolIdAndAccountIdAndStatus(schoolClass.getSchoolId(), teacherId, SchoolStaffStatus.ACTIVE)) {
            throw new ResourceNotFoundException("SchoolStaff", teacherId);
        }
        TeacherAssignment assignment = assignments.findBySchoolClassIdAndTeacherAccountId(classId, teacherId)
                .map(existing -> {
                    existing.reactivate();
                    return existing;
                })
                .orElseGet(() -> assignments.save(TeacherAssignment.assign(classId, teacherId)));
        audit(assignedBy, "TEACHER_ASSIGNED", "SCHOOL_CLASS", classId, Map.of("teacherAccountId", teacherId.toString()));
        return assignment;
    }

    @Transactional
    public void unassign(UUID classId, UUID teacherId, UUID unassignedBy) {
        TeacherAssignment assignment = assignments.findBySchoolClassIdAndTeacherAccountId(classId, teacherId)
                .filter(a -> a.getStatus() == TeacherAssignmentStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("TeacherAssignment", teacherId));
        assignment.remove();
        audit(unassignedBy, "TEACHER_UNASSIGNED", "SCHOOL_CLASS", classId, Map.of("teacherAccountId", teacherId.toString()));
    }

    @Transactional(readOnly = true)
    public List<Account> teachersOf(UUID classId) {
        return assignments.findBySchoolClassIdAndStatus(classId, TeacherAssignmentStatus.ACTIVE).stream()
                .map(a -> accounts.findById(a.getTeacherAccountId()).orElseThrow())
                .toList();
    }

    // ------------------------------------------------------------------ the teacher's side

    @Transactional(readOnly = true)
    public List<Invitation> openInvitations(UUID accountId) {
        return staff.findByAccountIdAndStatus(accountId, SchoolStaffStatus.INVITED).stream()
                .map(m -> new Invitation(m, schools.findById(m.getSchoolId()).orElseThrow()))
                .toList();
    }

    @Transactional
    public void respond(UUID membershipId, UUID accountId, boolean accept) {
        // Someone else's invitation reads as not found: its id is not permission.
        SchoolStaff membership = staff.findForUpdate(membershipId)
                .filter(m -> m.getAccountId().equals(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("SchoolStaff", membershipId));
        Account account = accounts.findById(accountId).orElseThrow();
        if (account.getPlatformRole() != PlatformRole.TEACHER) {
            throw new AccessDeniedException("Only teachers can join a school's teaching staff");
        }
        Instant now = Instant.now();
        if (accept) {
            membership.accept(now);
        } else {
            membership.decline(now);
        }
        audit(accountId, accept ? "SCHOOL_STAFF_JOINED" : "SCHOOL_STAFF_DECLINED", "SCHOOL", membership.getSchoolId(),
                Map.of("accountId", accountId.toString()));
    }

    // ------------------------------------------------------------------

    private void audit(UUID actorId, String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        Account actor = accounts.findById(actorId).orElseThrow();
        auditService.record(new RecordAuditEvent(null, actorId, AuditActorType.valueOf(actor.getPlatformRole().name()),
                action, resourceType, resourceId, metadata));
    }

}
