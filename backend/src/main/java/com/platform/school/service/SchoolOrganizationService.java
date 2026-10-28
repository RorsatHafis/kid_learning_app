package com.platform.school.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolOrganization;
import com.platform.school.repository.SchoolOrganizationRepository;
import com.platform.school.repository.SchoolRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M12 B0: school organizations and their branches. The organization's owner oversees all its branches and can add
 * one; a branch is a `School` as before - its own principal, classes, calendar, staff, plan and payment (a new branch
 * waits for its own payment, like any school: decision S7). A branch principal who doesn't own the organization sees
 * only their branch.
 */
@Service
public class SchoolOrganizationService {

    private final SchoolOrganizationRepository organizations;
    private final SchoolRepository schools;
    private final AccountRepository accounts;
    private final AuditService audit;

    public SchoolOrganizationService(SchoolOrganizationRepository organizations, SchoolRepository schools, AccountRepository accounts,
                                     AuditService audit) {
        this.organizations = organizations;
        this.schools = schools;
        this.accounts = accounts;
        this.audit = audit;
    }

    public record OrganizationWithBranches(SchoolOrganization organization, List<School> branches, boolean owner) {}

    /** The organizations the account owns (every branch), and those where it runs a branch (that branch only). */
    @Transactional(readOnly = true)
    public List<OrganizationWithBranches> mine(UUID accountId) {
        Map<UUID, OrganizationWithBranches> result = new LinkedHashMap<>();
        for (SchoolOrganization owned : organizations.findByOwnerAccountId(accountId)) {
            result.put(owned.getId(), new OrganizationWithBranches(owned, schools.findByOrganizationIdOrderByName(owned.getId()), true));
        }
        for (School run : schools.findByPrincipalAccountId(accountId)) {
            if (result.containsKey(run.getOrganizationId())) {
                continue;
            }
            SchoolOrganization organization = organizations.findById(run.getOrganizationId()).orElseThrow();
            List<School> own = schools.findByPrincipalAccountId(accountId).stream()
                    .filter(s -> s.getOrganizationId().equals(organization.getId())).toList();
            result.put(organization.getId(), new OrganizationWithBranches(organization, own, false));
        }
        return List.copyOf(result.values());
    }

    /**
     * A new branch of the organization, run at first by its owner. Same name as an active branch: that branch (a
     * retried request makes no second one).
     */
    @Transactional
    public School addBranch(UUID organizationId, String name, UUID actorId) {
        SchoolOrganization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("School organization", organizationId));
        Account actor = accounts.findForUpdate(actorId).orElseThrow(() -> new ResourceNotFoundException("Account", actorId));
        if (!actorId.equals(organization.getOwnerAccountId()) && actor.getPlatformRole() != PlatformRole.ADMIN) {
            throw new ResourceNotFoundException("School organization", organizationId); // not theirs: as if it didn't exist
        }
        if (organization.isArchived()) {
            throw new IllegalStateException("This school organization is archived.");
        }
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw new IllegalArgumentException("A branch name needs 1 to 200 characters.");
        }
        var same = schools.findByOrganizationIdOrderByName(organizationId).stream()
                .filter(s -> !s.isArchived() && s.getName().strip().equalsIgnoreCase(trimmed)).findFirst();
        if (same.isPresent()) {
            return same.get();
        }
        UUID principal = actor.getPlatformRole() == PlatformRole.ADMIN ? organization.getOwnerAccountId() : actorId;
        School branch = schools.save(School.create(trimmed, principal, organizationId));
        audit.record(new RecordAuditEvent(null, actorId, AuditActorType.valueOf(actor.getPlatformRole().name()), "SCHOOL_BRANCH_CREATED",
                "SCHOOL", branch.getId(), Map.of("organizationId", organizationId, "name", trimmed)));
        return branch;
    }

    /** The two schools are branches of one organization (calendar copies never cross organizations). */
    public void requireSameOrganization(School a, School b) {
        if (!a.getOrganizationId().equals(b.getOrganizationId())) {
            throw new AccessDeniedException("These schools are not branches of the same organization");
        }
    }
}
