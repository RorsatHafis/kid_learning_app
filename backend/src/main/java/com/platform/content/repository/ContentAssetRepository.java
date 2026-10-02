package com.platform.content.repository;

import com.platform.content.entity.ContentAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentAssetRepository extends JpaRepository<ContentAsset, UUID> {

    /** Platform media (no owner) already uploaded with these bytes. */
    Optional<ContentAsset> findBySha256AndOwnerAccountIdIsNull(String sha256);

    Optional<ContentAsset> findBySha256AndOwnerAccountId(String sha256, UUID ownerAccountId);

    /** Any asset still holding these bytes (the stored file is shared by every owner's copy). */
    boolean existsBySha256(String sha256);

    /*
     * The media library (X13): what an author's content may use - platform media, plus their own when
     * they are not an administrator (ownerId null = an administrator, who manages platform media). Every
     * filter is optional: kind, a case-insensitive match on title or description, archived or not, and
     * "used by no question" (unused). Newest first.
     */
    String LIBRARY_FROM = """
            FROM content_assets a
            WHERE a.sha256 IS NOT NULL
              AND (a.owner_account_id IS NULL OR (CAST(:ownerId AS uuid) IS NOT NULL AND a.owner_account_id = CAST(:ownerId AS uuid)))
              AND (CAST(:type AS varchar) IS NULL OR a.asset_type = CAST(:type AS varchar))
              AND (CAST(:pattern AS varchar) IS NULL OR a.title ILIKE CAST(:pattern AS varchar)
                   OR a.description ILIKE CAST(:pattern AS varchar))
              AND (:archived = (a.archived_at IS NOT NULL))
              AND (:unused = false OR NOT (
                   EXISTS (SELECT 1 FROM question_versions v WHERE v.prompt_image_asset_id = a.id OR v.prompt_audio_asset_id = a.id)
                   OR EXISTS (SELECT 1 FROM question_options o WHERE o.image_asset_id = a.id OR o.audio_asset_id = a.id)))
            """;

    @Query(value = "SELECT a.id " + LIBRARY_FROM + " ORDER BY a.created_at DESC, a.id LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<UUID> searchLibrary(@Param("ownerId") UUID ownerId, @Param("type") String type, @Param("pattern") String pattern,
                             @Param("archived") boolean archived, @Param("unused") boolean unused,
                             @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT count(*) " + LIBRARY_FROM, nativeQuery = true)
    long countLibrary(@Param("ownerId") UUID ownerId, @Param("type") String type, @Param("pattern") String pattern,
                      @Param("archived") boolean archived, @Param("unused") boolean unused);

    /** How many question versions use each asset, for a page of the library in one statement. */
    interface UseCount {
        UUID getAssetId();

        long getVersions();
    }

    @Query(nativeQuery = true, value = """
            SELECT u.asset_id AS assetId, count(DISTINCT u.version_id) AS versions FROM (
                SELECT v.prompt_image_asset_id AS asset_id, v.id AS version_id FROM question_versions v WHERE v.prompt_image_asset_id IN (:ids)
                UNION ALL SELECT v.prompt_audio_asset_id, v.id FROM question_versions v WHERE v.prompt_audio_asset_id IN (:ids)
                UNION ALL SELECT o.image_asset_id, o.question_version_id FROM question_options o WHERE o.image_asset_id IN (:ids)
                UNION ALL SELECT o.audio_asset_id, o.question_version_id FROM question_options o WHERE o.audio_asset_id IN (:ids)
            ) u GROUP BY u.asset_id
            """)
    List<UseCount> useCounts(@Param("ids") Collection<UUID> assetIds);

    /** Where one asset is used: each question version and the field (PROMPT_IMAGE, PROMPT_AUDIO, ANSWER_IMAGE, ANSWER_AUDIO). */
    interface Use {
        UUID getVersionId();

        String getField();
    }

    @Query(nativeQuery = true, value = """
            SELECT DISTINCT u.version_id AS versionId, u.field AS field FROM (
                SELECT v.id AS version_id, 'PROMPT_IMAGE' AS field FROM question_versions v WHERE v.prompt_image_asset_id = :id
                UNION ALL SELECT v.id, 'PROMPT_AUDIO' FROM question_versions v WHERE v.prompt_audio_asset_id = :id
                UNION ALL SELECT o.question_version_id, 'ANSWER_IMAGE' FROM question_options o WHERE o.image_asset_id = :id
                UNION ALL SELECT o.question_version_id, 'ANSWER_AUDIO' FROM question_options o WHERE o.audio_asset_id = :id
            ) u
            """)
    List<Use> usesOf(@Param("id") UUID assetId);

}
