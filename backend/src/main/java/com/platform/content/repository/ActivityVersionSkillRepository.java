package com.platform.content.repository;

import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.ActivityVersionSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityVersionSkillRepository extends JpaRepository<ActivityVersionSkill, UUID> {

    List<ActivityVersionSkill> findByActivityVersionIdOrderByCreatedAtAscIdAsc(UUID activityVersionId);

    List<ActivityVersionSkill> findByActivityVersionIdIn(Collection<UUID> activityVersionIds);

    Optional<ActivityVersionSkill> findByActivityVersionIdAndSkillId(UUID activityVersionId, UUID skillId);

    /**
     * The published activity versions that practise a skill (at most one per activity, V20), in the
     * order they were tagged: what paths place, what reinforcement and challenge choose from. X14: an
     * archived activity is not placed anew (new paths, adaptive, Smart Review, class practice, publish
     * checks); what is already on a child's path stays.
     */
    @Query("""
            select v from ActivityVersion v, ActivityVersionSkill t
            where t.activityVersionId = v.id and t.skillId = :skillId
              and v.status = com.platform.common.entity.PublicationStatus.PUBLISHED
              and exists (select a.id from Activity a where a.id = v.activityId
                          and a.status = com.platform.common.entity.CatalogStatus.ACTIVE)
            order by t.createdAt, v.activityId
            """)
    List<ActivityVersion> findPublishedPractising(@Param("skillId") UUID skillId);

    /** Every version, in any state, that practises a skill (authoring views: drafts count too). */
    @Query("""
            select v from ActivityVersion v, ActivityVersionSkill t
            where t.activityVersionId = v.id and t.skillId = :skillId
            order by t.createdAt, v.activityId, v.versionNumber
            """)
    List<ActivityVersion> findAllPractising(@Param("skillId") UUID skillId);
}
