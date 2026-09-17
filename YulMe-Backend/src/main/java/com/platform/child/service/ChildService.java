package com.platform.child.service;

import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.Membership;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Minimal parent-to-child access surface. Before this, a logged-in parent had no
 * legitimate way to discover or create a child: {@code AccountRegistrationWriter}
 * creates an Account, a Family, and an OWNER Membership on registration, but never
 * a Child, and no controller anywhere exposed {@link ChildRepository}. That left
 * the golden validator journey (login -&gt; child -&gt; enrollment -&gt; ...) with no real
 * step 2. This class is deliberately thin - list and create only, both scoped to
 * the caller's own ACTIVE family membership(s), never a client-supplied family or
 * parent id (same data-ownership posture as {@link com.platform.family.service.FamilyAccessGuard}).
 * No editing, archiving, or consent workflow here - {@link Child#archive()} and
 * {@code ConsentService} already exist for whenever that's needed and aren't wired
 * into anything else in the golden path either, so wiring them in here now would
 * be scope the mission doesn't ask for.
 */
@Service
public class ChildService {

    private final ChildRepository childRepository;
    private final MembershipRepository membershipRepository;

    public ChildService(ChildRepository childRepository, MembershipRepository membershipRepository) {
        this.childRepository = childRepository;
        this.membershipRepository = membershipRepository;
    }

    /** Every child in every family this account currently has ACTIVE membership in. */
    @Transactional(readOnly = true)
    public List<Child> listForAccount(UUID accountId) {
        return activeFamilyIds(accountId).stream()
                .flatMap(familyId -> childRepository.findByFamilyId(familyId).stream())
                .toList();
    }

    /**
     * Adds a child to the calling account's family. Public registration
     * (PARENT-only - see {@code AuthController}) always creates exactly one ACTIVE
     * membership for a new account, so the only account that could reach this
     * with none is a non-PARENT account (TEACHER/PRINCIPAL/ADMIN never join a
     * family) - a 404 here is the correct "you have nowhere to put a child"
     * response for that case, not a 500.
     */
    @Transactional
    public Child createForAccount(UUID accountId, String displayName, LocalDate dateOfBirth) {
        UUID familyId = activeFamilyIds(accountId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Family for account", accountId));

        return childRepository.save(Child.enroll(familyId, displayName, dateOfBirth));
    }

    private List<UUID> activeFamilyIds(UUID accountId) {
        return membershipRepository.findByAccountId(accountId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(Membership::getFamilyId)
                .toList();
    }

}
