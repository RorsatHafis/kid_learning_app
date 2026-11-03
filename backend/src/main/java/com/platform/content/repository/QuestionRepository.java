package com.platform.content.repository;

import com.platform.common.entity.CatalogStatus;
import com.platform.content.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for questions: by owner, platform-owned by subject, and the Studio's paged, searchable bank.
 */
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByOwnerAccountId(UUID ownerAccountId);

    List<Question> findBySubjectIdAndOwnerAccountIdIsNull(UUID subjectId);

    List<Question> findBySubjectIdAndOwnerAccountId(UUID subjectId, UUID ownerAccountId);

    List<Question> findBySubjectIdAndStatus(UUID subjectId, CatalogStatus status);

    /*
     * The question bank's search (StudioService#questions). One owner's active questions in a subject:
     * platform content when ownerId is null, otherwise that teacher's own. Every filter is optional:
     * type, the latest version's status, a skill the latest version is tagged with (PD-08), "has a published version" (live) and a
     * case-insensitive match on the latest version's prompt - in English or in its Khmer (LOCALIZATION.md), so an author
     * finds a question in either language they wrote it in. Ordered like the bank reads, by prompt.
     * {@code need} (StudioDtos.BankNeed): the latest version lacks an explanation, a hint or a read-aloud recording; for
     * KHMER this only leaves out typed-word questions - whether the Khmer is complete is decided in StudioService, by the
     * same rule the bank shows (QuestionTranslations).
     */
    String BANK_FROM = """
            FROM questions q
            LEFT JOIN LATERAL (SELECT v.id, v.prompt, v.status, v.hint, v.explanation, v.prompt_audio_asset_id FROM question_versions v
                               WHERE v.question_id = q.id ORDER BY v.version_number DESC LIMIT 1) latest ON true
            WHERE q.subject_id = :subjectId
              AND q.status = (CASE WHEN :archived THEN 'ARCHIVED' ELSE 'ACTIVE' END)
              AND ((CAST(:ownerId AS uuid) IS NULL AND q.owner_account_id IS NULL) OR q.owner_account_id = CAST(:ownerId AS uuid))
              AND (CAST(:type AS varchar) IS NULL OR q.question_type = CAST(:type AS varchar))
              AND (CAST(:status AS varchar) IS NULL OR latest.status = CAST(:status AS varchar))
              AND (CAST(:skillId AS uuid) IS NULL OR EXISTS (SELECT 1 FROM question_version_skills s
                                                             WHERE s.question_version_id = latest.id AND s.skill_id = CAST(:skillId AS uuid)))
              AND (:live = false OR EXISTS (SELECT 1 FROM question_versions p
                                            WHERE p.question_id = q.id AND p.status = 'PUBLISHED'))
              AND (CAST(:pattern AS varchar) IS NULL OR latest.prompt ILIKE CAST(:pattern AS varchar)
                   OR EXISTS (SELECT 1 FROM content_localizations k
                              WHERE k.content_type = 'QUESTION_VERSION' AND k.content_id = latest.id AND k.locale = 'km'
                                AND k.field_name = 'prompt' AND k.localized_text ILIKE CAST(:pattern AS varchar)))
              AND (CAST(:need AS varchar) IS NULL
                   OR (CAST(:need AS varchar) = 'EXPLANATION' AND NULLIF(btrim(latest.explanation), '') IS NULL)
                   OR (CAST(:need AS varchar) = 'HINT' AND NULLIF(btrim(latest.hint), '') IS NULL)
                   OR (CAST(:need AS varchar) = 'READ_ALOUD' AND latest.prompt_audio_asset_id IS NULL)
                   OR (CAST(:need AS varchar) = 'KHMER' AND q.question_type <> 'SHORT_ANSWER'))
            """;

    @Query(value = "SELECT q.id " + BANK_FROM + " ORDER BY lower(latest.prompt) NULLS LAST, q.id LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<UUID> searchBank(@Param("subjectId") UUID subjectId, @Param("ownerId") UUID ownerId, @Param("type") String type,
                          @Param("status") String status, @Param("skillId") UUID skillId, @Param("live") boolean live,
                          @Param("pattern") String pattern, @Param("archived") boolean archived, @Param("need") String need,
                          @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT count(*) " + BANK_FROM, nativeQuery = true)
    long countBank(@Param("subjectId") UUID subjectId, @Param("ownerId") UUID ownerId, @Param("type") String type,
                   @Param("status") String status, @Param("skillId") UUID skillId, @Param("live") boolean live,
                   @Param("pattern") String pattern, @Param("archived") boolean archived, @Param("need") String need);

}
