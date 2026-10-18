package com.platform.child.repository;

import com.platform.child.entity.ChildSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChildSettingsRepository extends JpaRepository<ChildSettings, UUID> {

    Optional<ChildSettings> findByChildId(UUID childId);
}
