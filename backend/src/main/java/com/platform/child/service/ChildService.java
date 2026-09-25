package com.platform.child.service;

import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.Membership;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.MembershipRepository;
import com.platform.learning.service.EnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>Battle 2B: {@link #completeAutoEnrollment} attempts best-effort automatic
 * enrollment (see {@link EnrollmentService#autoEnrollIfUnambiguous}) immediately
 * after a child is created, so a parent does not need to discover the separate
 * explicit enrollment endpoint - but never at the cost of child creation itself;
 * see {@link #completeAutoEnrollment}'s own javadoc for why that is a second,
 * separate call rather than being folded into {@link #createForAccount}.
 */
@Service
public class ChildService {

    private static final Logger log = LoggerFactory.getLogger(ChildService.class);

    private final ChildRepository childRepository;
    private final MembershipRepository membershipRepository;
    private final EnrollmentService enrollmentService;

    public ChildService(ChildRepository childRepository, MembershipRepository membershipRepository,
                         EnrollmentService enrollmentService) {
        this.childRepository = childRepository;
        this.membershipRepository = membershipRepository;
        this.enrollmentService = enrollmentService;
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

    /**
     * See {@link EnrollmentService#autoEnrollIfUnambiguous} for the resolution
     * rules. Deliberately a separate call from {@link #createForAccount} - not
     * invoked from inside it - and deliberately not itself {@code @Transactional}:
     * {@code autoEnrollIfUnambiguous} runs in its own {@code REQUIRES_NEW}
     * transaction, so it needs {@link #createForAccount}'s transaction to have
     * already committed (otherwise its own DB connection cannot yet see the
     * child row it needs to enroll - an uncommitted row is invisible outside the
     * transaction that wrote it), and this method must have no transaction of
     * its own for the same reason a caught exception here must not retroactively
     * poison an already-finished commit. {@link com.platform.child.web.ChildController#addChild}
     * is what actually sequences the two calls.
     */
    public void completeAutoEnrollment(Child child) {
        try {
            enrollmentService.autoEnrollIfUnambiguous(child);
        } catch (RuntimeException ex) {
            log.warn("Auto-enrollment failed for newly created child {}; the child was still created successfully",
                    child.getId(), ex);
        }
    }

    private List<UUID> activeFamilyIds(UUID accountId) {
        return membershipRepository.findByAccountId(accountId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(Membership::getFamilyId)
                .toList();
    }

}
