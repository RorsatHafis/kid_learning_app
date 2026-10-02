package com.platform.content.repository;

import com.platform.common.entity.CatalogStatus;
import com.platform.content.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByOwnerAccountId(UUID ownerAccountId);

    /** Platform lessons (no owner) - what an ADMIN authors and manages. */
    List<Lesson> findByOwnerAccountIdIsNull();

    List<Lesson> findBySubjectIdAndStatus(UUID subjectId, CatalogStatus status);

    /*
     * The Studio's lessons (StudioService#lessons): one owner's active lessons (platform lessons when
     * ownerId is null), optionally in one subject, optionally those whose title or one of whose
     * activities' titles contains the search pattern. By title.
     */
    String STUDIO_FROM = """
            FROM lessons l
            WHERE ((:archived = false AND l.status = 'ACTIVE')
                   OR (:archived = true AND (l.status = 'ARCHIVED'
                       OR EXISTS (SELECT 1 FROM activities x WHERE x.lesson_id = l.id AND x.status = 'ARCHIVED'))))
              AND ((CAST(:ownerId AS uuid) IS NULL AND l.owner_account_id IS NULL) OR l.owner_account_id = CAST(:ownerId AS uuid))
              AND (CAST(:subjectId AS uuid) IS NULL OR l.subject_id = CAST(:subjectId AS uuid))
              AND (CAST(:pattern AS varchar) IS NULL OR l.title ILIKE CAST(:pattern AS varchar)
                   OR EXISTS (SELECT 1 FROM activities a WHERE a.lesson_id = l.id AND a.title ILIKE CAST(:pattern AS varchar)))
            """;

    @Query(value = "SELECT l.id " + STUDIO_FROM + " ORDER BY lower(l.title), l.id LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<UUID> searchStudio(@Param("ownerId") UUID ownerId, @Param("subjectId") UUID subjectId, @Param("pattern") String pattern,
                            @Param("archived") boolean archived, @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT count(*) " + STUDIO_FROM, nativeQuery = true)
    long countStudio(@Param("ownerId") UUID ownerId, @Param("subjectId") UUID subjectId, @Param("pattern") String pattern,
                     @Param("archived") boolean archived);

}
