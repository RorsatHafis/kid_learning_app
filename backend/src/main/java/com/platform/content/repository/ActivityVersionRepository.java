package com.platform.content.repository;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivityVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for activity versions: an exact version, the newest, all of them in order, or the newest in a given
 * publication status.
 */
public interface ActivityVersionRepository extends JpaRepository<ActivityVersion, UUID> {

    Optional<ActivityVersion> findByActivityIdAndVersionNumber(UUID activityId, int versionNumber);

    Optional<ActivityVersion> findTopByActivityIdOrderByVersionNumberDesc(UUID activityId);

    List<ActivityVersion> findByActivityIdOrderByVersionNumberAsc(UUID activityId);

    List<ActivityVersion> findByActivityIdInOrderByVersionNumberAsc(java.util.Collection<UUID> activityIds);

    Optional<ActivityVersion> findFirstByActivityIdAndStatusOrderByVersionNumberDesc(UUID activityId, PublicationStatus status);

}