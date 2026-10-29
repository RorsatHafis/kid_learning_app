package com.platform.content.repository;

import com.platform.content.entity.ContentLocalization;
import com.platform.content.entity.LocalizableContentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for translated content text.
 */
public interface ContentLocalizationRepository extends JpaRepository<ContentLocalization, UUID> {

    // Matches uq_content_localizations_target (V12 migration).
    Optional<ContentLocalization> findByContentTypeAndContentIdAndLocaleAndFieldName(
            LocalizableContentType contentType, UUID contentId, String locale, String fieldName);

    // "Every translated field for this content item in this locale" - what a content
    // API response assembling a localized QuestionVersion/ActivityVersion/LessonVersion needs.
    List<ContentLocalization> findByContentTypeAndContentIdAndLocale(
            LocalizableContentType contentType, UUID contentId, String locale);

    /** One locale's translations of many items of one type (a list's names in one query). */
    List<ContentLocalization> findByContentTypeAndContentIdInAndLocale(
            LocalizableContentType contentType, java.util.Collection<UUID> contentIds, String locale);

    /** Every translated field of one content item in one locale goes (a draft's Khmer is rewritten whole). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByContentTypeAndContentIdAndLocale(LocalizableContentType contentType, UUID contentId, String locale);

}