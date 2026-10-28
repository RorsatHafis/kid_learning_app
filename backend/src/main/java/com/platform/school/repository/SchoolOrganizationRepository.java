package com.platform.school.repository;

import com.platform.school.entity.SchoolOrganization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SchoolOrganizationRepository extends JpaRepository<SchoolOrganization, UUID> {

    List<SchoolOrganization> findByOwnerAccountId(UUID ownerAccountId);
}
