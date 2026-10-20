package com.platform.family.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.family.entity.Membership;

/**
 * Persistence for memberships: one account's place in one family, all of an account's families, or all of a
 * family's parents.
 */
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByFamilyIdAndAccountId(UUID familyId, UUID accountId);

    List<Membership> findByAccountId(UUID accountId);

    List<Membership> findByFamilyId(UUID familyId);
    
}
