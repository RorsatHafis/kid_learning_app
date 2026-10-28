package com.platform.school.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolClass;
import com.platform.school.entity.TeacherAssignmentStatus;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.repository.TeacherAssignmentRepository;
import com.platform.subscription.service.SchoolEntitlementService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Central server-side authorization for school-scoped requests.
 * <p>
 * Who may act is decided first, so someone outside the school is refused (403) without learning anything about
 * it. Then, for a principal or teacher, the school must be paid (Y44, {@link SchoolEntitlementService#requirePaid}):
 * school services stop when it isn't, answered as 402. Administrators run the platform and keep access to invoice
 * and reconcile an unpaid school; {@link #requireSchoolBilling} is the principal's way to the school's billing alone.
 */
@Service
public class SchoolAccessGuard {

    private final AccountRepository accounts;
    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final TeacherAssignmentRepository assignments;
    private final SchoolEntitlementService entitlements;
    private final com.platform.school.repository.SchoolOrganizationRepository organizations;

    public SchoolAccessGuard(AccountRepository accounts, SchoolRepository schools, SchoolClassRepository classes,
                             TeacherAssignmentRepository assignments, SchoolEntitlementService entitlements,
                             com.platform.school.repository.SchoolOrganizationRepository organizations) {

        this.organizations = organizations;

        this.accounts = accounts;
        this.schools = schools;
        this.classes = classes;
        this.assignments = assignments;
        this.entitlements = entitlements;

    }

    public Account requireStaff(UUID accountId) {

        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() == PlatformRole.PARENT) {
            throw new AccessDeniedException("Parent accounts cannot access school resources");
        }
        return account;

    }

    /** The school's principal (or an administrator) running the school: needs the school paid. */
    public School requireSchoolManage(UUID accountId, UUID schoolId) {

        Account account = requireStaff(accountId);
        School school = schoolManagedBy(account, schoolId);
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            entitlements.requirePaid(schoolId);
        }
        return school;

    }

    /**
     * The school's billing - its plan, payment state, invoices and paying them - for its principal (or an
     * administrator). Open while the school is unpaid: it is how a school gets paid again; nothing else is.
     */
    public School requireSchoolBilling(UUID accountId, UUID schoolId) {

        return schoolManagedBy(requireStaff(accountId), schoolId);

    }

    /**
     * The people who work with a class's children (PD-01): the principal of its school, or a teacher
     * assigned to it, while the school is paid. Not an administrator: running the platform is no way into
     * children's data (CLAUDE.md §10, §22). Anyone else is refused as if the class did not exist to them.
     */
    public SchoolClass requireClassStaff(UUID accountId, UUID classId) {

        Account account = requireStaff(accountId);
        SchoolClass schoolClass = classes.findById(classId).orElseThrow(() -> new ResourceNotFoundException("SchoolClass", classId));
        boolean allowed = switch (account.getPlatformRole()) {
            case PRINCIPAL -> schools.findById(schoolClass.getSchoolId())
                    .map(school -> accountId.equals(school.getPrincipalAccountId()))
                    .orElse(false);
            case TEACHER -> assignments.existsBySchoolClassIdAndTeacherAccountIdAndStatus(classId, accountId, TeacherAssignmentStatus.ACTIVE);
            default -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("Only the class's teachers and its school's principal work with its children");
        }

        entitlements.requirePaid(schoolClass.getSchoolId());
        return schoolClass;

    }

    /** A class's administration: an administrator, its school's principal, or a teacher assigned to it (paid school). */
    public SchoolClass requireClassAccess(UUID accountId, UUID classId) {

        Account account = requireStaff(accountId);
        SchoolClass schoolClass = classes.findById(classId).orElseThrow(() -> new ResourceNotFoundException("SchoolClass", classId));
        if (account.getPlatformRole() == PlatformRole.ADMIN) {
            return schoolClass;
        }

        if (account.getPlatformRole() == PlatformRole.PRINCIPAL) {
            requireSchoolManage(accountId, schoolClass.getSchoolId());
            return schoolClass;
        }

        if (!assignments.existsBySchoolClassIdAndTeacherAccountIdAndStatus(classId, accountId, TeacherAssignmentStatus.ACTIVE)) {
            throw new AccessDeniedException("Teacher is not assigned to this class");
        }
        entitlements.requirePaid(schoolClass.getSchoolId());
        return schoolClass;

    }

    /** For a school service reached another way (a teacher joining its staff, a family joining a class). */
    public void requirePaidSchool(UUID schoolId) {

        entitlements.requirePaid(schoolId);

    }

    /**
     * M12 B1: the owner of the school's organization runs every branch's configuration (calendar, classes, staff,
     * plan) - not the children's data in a branch's classes (requireClassStaff stays the branch's principal and teachers).
     */
    private boolean ownsOrganizationOf(Account account, School school) {
        return organizations.findById(school.getOrganizationId())
                .map(o -> account.getId().equals(o.getOwnerAccountId())).orElse(false);
    }

    private School schoolManagedBy(Account account, UUID schoolId) {

        School school = schools.findById(schoolId).orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
        if (account.getPlatformRole() != PlatformRole.ADMIN && !account.getId().equals(school.getPrincipalAccountId())
                && !ownsOrganizationOf(account, school)) {
            throw new AccessDeniedException("Account is not allowed to manage this school");
        }
        return school;

    }

}
