package com.platform.family.service;

import com.platform.child.entity.Child;
import com.platform.child.entity.ChildStatus;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.MembershipRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M10b: who may see or use one child's learning besides the child's family - a trusted caregiver a parent invited for
 * that child (child_caregivers). Two levels, each named at the endpoint that admits it:
 *
 * <ul>
 *   <li>{@link #requireViewer}: the reads a parent sees about the child's learning (paths, skills, what changed,
 *       insights, history) - family, or a caregiver of either scope;</li>
 *   <li>{@link #requireLearner}: the child's own learning space (playing, answering) - family, or a caregiver who may
 *       open it.</li>
 * </ul>
 *
 * Everything else about a child - settings, time, subjects, deletion, export, schools, billing - stays with the family
 * ({@link FamilyAccessGuard}), which a caregiver never passes. A caregiver who isn't one (anymore) is answered exactly
 * like anyone without access: 404, the same body.
 */
@Service
public class ChildAccess {

    public enum Relation { FAMILY, CAREGIVER }

    /** {@code caregiverScope} is VIEW_PROGRESS or OPEN_SPACE for a caregiver, null for the family. */
    public record Access(Child child, Relation relation, String caregiverScope) {

        public boolean family() {
            return relation == Relation.FAMILY;
        }
    }

    private static final List<String> ANY_SCOPE = List.of("VIEW_PROGRESS", "OPEN_SPACE");
    private static final List<String> OPENS_SPACE = List.of("OPEN_SPACE");

    private final MembershipRepository memberships;
    private final ChildRepository children;
    private final NamedParameterJdbcTemplate jdbc;

    public ChildAccess(MembershipRepository memberships, ChildRepository children, NamedParameterJdbcTemplate jdbc) {
        this.memberships = memberships;
        this.children = children;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Access requireViewer(UUID accountId, UUID childId) {
        return require(accountId, childId, ANY_SCOPE);
    }

    @Transactional(readOnly = true)
    public Access requireLearner(UUID accountId, UUID childId) {
        return require(accountId, childId, OPENS_SPACE);
    }

    /**
     * The family check is FamilyAccessGuard#requireChildAccess's own (an ACTIVE membership, an ACTIVE child), asked here
     * rather than by catching its refusal: a refusal inside a transaction would leave it rollback-only.
     */
    private Access require(UUID accountId, UUID childId, List<String> scopes) {
        Child child = children.findById(childId).filter(c -> c.getStatus() == ChildStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        boolean family = memberships.findByFamilyIdAndAccountId(child.getFamilyId(), accountId)
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE).isPresent();
        if (family) {
            return new Access(child, Relation.FAMILY, null);
        }
        List<String> held = jdbc.queryForList("""
                SELECT scope FROM child_caregivers WHERE child_id = :c AND account_id = :a AND status = 'ACTIVE'
                """, Map.of("c", childId, "a", accountId), String.class);
        if (held.size() != 1 || !scopes.contains(held.getFirst())) {
            throw new ResourceNotFoundException("Child", childId);
        }
        return new Access(child, Relation.CAREGIVER, held.getFirst());
    }

}
