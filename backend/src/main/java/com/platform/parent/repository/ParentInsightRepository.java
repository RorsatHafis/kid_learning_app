package com.platform.parent.repository;

import com.platform.parent.entity.ParentInsight;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for parent insights: a child's newest insights, the latest per subject, and a row lock on the child
 * so two insights for one event can't be written at once.
 */
public interface ParentInsightRepository extends JpaRepository<ParentInsight, UUID> {

    /** The most recent snapshots, newest first, at most {@code limit} of them. */
    List<ParentInsight> findByChildIdOrderByOccurredAtDesc(UUID childId, Limit limit);

    /**
     * The latest recorded snapshot of each thing an insight is about - a learning objective or a
     * review item - for one child.
     */
    @Query(value = """
            select distinct on (coalesce(supporting_data ->> 'learningObjectiveId', supporting_data ->> 'reviewItemId'))
                   pi.*
            from parent_insights pi
            where pi.child_id = :childId
            order by coalesce(supporting_data ->> 'learningObjectiveId', supporting_data ->> 'reviewItemId'), occurred_at desc
            """, nativeQuery = true)
    List<ParentInsight> findLatestPerSubject(@Param("childId") UUID childId);

    /** Serialises snapshot recording for one child (the child's row lock). */
    @Query(value = "select cast(id as text) from children where id = :childId for update", nativeQuery = true)
    List<String> lockChild(@Param("childId") UUID childId);

}
