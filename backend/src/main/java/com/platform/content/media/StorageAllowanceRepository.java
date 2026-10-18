package com.platform.content.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StorageAllowanceRepository extends JpaRepository<StorageAllowance, UUID> {
    Optional<StorageAllowance> findByAccountId(UUID accountId);
}
