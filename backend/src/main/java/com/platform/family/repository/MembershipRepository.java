package com.platform.family.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.family.entity.Membership;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByFamilyIdAndAccountId(UUID familyId, UUID accountId);

    List<Membership> findByAccountId(UUID accountId);
    
}
