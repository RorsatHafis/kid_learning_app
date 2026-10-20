package com.platform.child.repository;

import com.platform.child.entity.ChildSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for a child's settings (one row per child).
 */
public interface ChildSettingsRepository extends JpaRepository<ChildSettings, UUID> {

    Optional<ChildSettings> findByChildId(UUID childId);
}
