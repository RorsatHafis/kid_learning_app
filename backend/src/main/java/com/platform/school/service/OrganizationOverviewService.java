package com.platform.school.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.AcademicYear;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolChildAccessStatus;
import com.platform.school.entity.SchoolOrganization;
import com.platform.school.entity.SchoolStaffStatus;
import com.platform.school.repository.AcademicYearRepository;
import com.platform.school.repository.SchoolChildAccessRepository;
import com.platform.school.repository.SchoolOrganizationRepository;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.repository.SchoolStaffRepository;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.service.SchoolEntitlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M12 B2: an organization at a glance for its owner - each branch's size and whether it is paid, and the totals.
 * Counts only: no child, guardian or staff member is named here, so oversight never becomes a way round
 * {@code SchoolAccessGuard#requireClassStaff} (children's data stays with the people who teach or run the branch).
 * A child or teacher in two branches is counted once in the totals.
 */
@Service
public class OrganizationOverviewService {

    private static final List<SchoolStaffStatus> SEATED = List.of(SchoolStaffStatus.ACTIVE, SchoolStaffStatus.INVITED);

    private final SchoolOrganizationRepository organizations;
    private final SchoolRepository schools;
    private final AccountRepository accounts;
    private final SchoolEntitlementService entitlements;
    private final AcademicYearRepository years;
    private final SchoolChildAccessRepository access;
    private final SchoolStaffRepository staff;

    public OrganizationOverviewService(SchoolOrganizationRepository organizations, SchoolRepository schools, AccountRepository accounts,
                                       SchoolEntitlementService entitlements, AcademicYearRepository years,
                                       SchoolChildAccessRepository access, SchoolStaffRepository staff) {
        this.organizations = organizations;
        this.schools = schools;
        this.accounts = accounts;
        this.entitlements = entitlements;
        this.years = years;
        this.access = access;
        this.staff = staff;
    }

    /**
     * @param runByOwner  the owner is also this branch's principal
     * @param currentYear the branch's current academic year's name, or null
     */
    public record BranchFigures(UUID schoolId, String name, boolean archived, boolean runByOwner, int classes, int teachers,
                                int children, boolean paid, Instant paidUntil, boolean paymentPending, String currentYear) {
    }

    /** Totals over branches in use (archived ones are listed, not counted). */
    public record Overview(UUID organizationId, String name, int branchesInUse, int branchesPaid, long children, long teachers,
                           int classes, List<BranchFigures> branches) {
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID organizationId, UUID actorId) {
        SchoolOrganization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("School organization", organizationId));
        Account actor = accounts.findById(actorId).orElseThrow(() -> new ResourceNotFoundException("Account", actorId));
        if (!actorId.equals(organization.getOwnerAccountId()) && actor.getPlatformRole() != PlatformRole.ADMIN) {
            throw new ResourceNotFoundException("School organization", organizationId); // not theirs: as if it didn't exist
        }
        List<School> branches = schools.findByOrganizationIdOrderByName(organizationId);
        List<BranchFigures> figures = branches.stream().map(branch -> {
            Map<EntitlementKey, Integer> usage = entitlements.usage(branch.getId());
            SchoolEntitlementService.SchoolAccess paid = entitlements.access(branch.getId());
            String current = years.findBySchoolIdOrderByStartsOnDesc(branch.getId()).stream()
                    .filter(AcademicYear::isCurrent).map(AcademicYear::getName).findFirst().orElse(null);
            return new BranchFigures(branch.getId(), branch.getName(), branch.isArchived(),
                    organization.getOwnerAccountId().equals(branch.getPrincipalAccountId()),
                    usage.getOrDefault(EntitlementKey.SCHOOL_CLASSES_MAX, 0), usage.getOrDefault(EntitlementKey.SCHOOL_TEACHERS_MAX, 0),
                    usage.getOrDefault(EntitlementKey.SCHOOL_CHILDREN_MAX, 0), paid.paid(), paid.paidUntil(), paid.paymentPending(), current);
        }).toList();
        List<BranchFigures> inUse = figures.stream().filter(b -> !b.archived()).toList();
        List<UUID> inUseIds = inUse.stream().map(BranchFigures::schoolId).toList();
        long children = inUseIds.isEmpty() ? 0 : access.countDistinctChildren(inUseIds, SchoolChildAccessStatus.ACTIVE);
        long teachers = inUseIds.isEmpty() ? 0 : staff.countDistinctStaff(inUseIds, SEATED);
        return new Overview(organizationId, organization.getName(), inUse.size(), (int) inUse.stream().filter(BranchFigures::paid).count(),
                children, teachers, inUse.stream().mapToInt(BranchFigures::classes).sum(), figures);
    }
}
