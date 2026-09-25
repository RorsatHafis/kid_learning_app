package com.platform.family.service;

import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.MembershipRepository;
import org.springframework.security.access.AccessDeniedException;
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
     * it. Throws {@link ResourceNotFoundException} for an unknown child (404) and
     * {@link AccessDeniedException} for a known child the account cannot access
     * (403) - kept distinct so a caller never learns whether an unrelated child id
     * exists from the HTTP status alone.
     */
    @Transactional(readOnly = true)
    public Child requireChildAccess(UUID accountId, UUID childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        boolean hasAccess = membershipRepository.findByFamilyIdAndAccountId(child.getFamilyId(), accountId)
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();

        if (!hasAccess) {
            throw new AccessDeniedException(
                    "Account %s does not have access to child %s".formatted(accountId, childId));
        }

        return child;
    }

}
