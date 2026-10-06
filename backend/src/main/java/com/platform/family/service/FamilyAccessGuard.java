package com.platform.family.service;

import com.platform.child.entity.ChildStatus;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Section 25 ("Backend must enforce access... do NOT rely on frontend route
 * hiding") for the one relationship that exists in the domain today: a parent
 * account may only act on children in a family it holds an ACTIVE membership in.
 * This intentionally does not wait for the full Role/JWT-authority rollout - every
 * child-scoped endpoint should call {@link #requireChildAccess} regardless of what
 * role machinery lands later, because parent-to-own-children scoping is a
 * data-ownership rule, not a role-permission rule (a TEACHER role will need its
 * own, separate class-scoped guard, not this one).
 */
@Service
public class FamilyAccessGuard {

    private final ChildRepository childRepository;
    private final MembershipRepository membershipRepository;

    public FamilyAccessGuard(ChildRepository childRepository, MembershipRepository membershipRepository) {
        this.childRepository = childRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Loads the child and verifies {@code accountId} has ACTIVE family access to
     * it. A child the account can't access is answered exactly like one that doesn't
     * exist (404, same body): a distinct 403 would tell an outsider that the id belongs
     * to a real child (CrossFamilyAccessSweepIntegrationTests).
     */
    @Transactional(readOnly = true)
    public Child requireChildAccess(UUID accountId, UUID childId) {
        Child child = requireChildRecord(accountId, childId);
        // A child being deleted has left learning and schools: answered like any child the caller can't see.
        if (child.getStatus() != ChildStatus.ACTIVE) {
            throw new ResourceNotFoundException("Child", childId);
        }
        return child;
    }

    /**
     * The family's access to the child's record whatever its state - for keeping a child whose
     * deletion was asked, and for the family's data export. Every learning flow uses
     * {@link #requireChildAccess} instead.
     */
    @Transactional(readOnly = true)
    public Child requireChildRecord(UUID accountId, UUID childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        boolean hasAccess = membershipRepository.findByFamilyIdAndAccountId(child.getFamilyId(), accountId)
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();

        if (!hasAccess) {
            throw new ResourceNotFoundException("Child", childId);
        }

        return child;
    }

}
