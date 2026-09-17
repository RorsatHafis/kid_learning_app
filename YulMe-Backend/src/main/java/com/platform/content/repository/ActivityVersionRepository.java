package com.platform.content.repository;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivityVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivityVersionRepository extends JpaRepository<ActivityVersion, UUID> {

    Optional<ActivityVersion> findByActivityIdAndVersionNumber(UUID activityId, int versionNumber);

    Optional<ActivityVersion> findTopByActivityIdOrderByVersionNumberDesc(UUID activityId);

    Optional<ActivityVersion> findFirstByActivityIdAndStatusOrderByVersionNumberDesc(UUID activityId, PublicationStatus status);

}