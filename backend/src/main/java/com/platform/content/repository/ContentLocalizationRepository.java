package com.platform.content.repository;

import com.platform.content.entity.ContentLocalization;
import com.platform.content.entity.LocalizableContentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentLocalizationRepository extends JpaRepository<ContentLocalization, UUID> {

    // Matches uq_content_localizations_target (V12 migration).
    Optional<ContentLocalization> findByContentTypeAndContentIdAndLocaleAndFieldName(
            LocalizableContentType contentType, UUID contentId, String locale, String fieldName);

    // "Every translated field for this content item in this locale" - what a content
    // API response assembling a localized QuestionVersion/ActivityVersion/LessonVersion needs.
    List<ContentLocalization> findByContentTypeAndContentIdAndLocale(
            LocalizableContentType contentType, UUID contentId, String locale);

}