package com.platform.content.service;

import com.platform.content.entity.ContentLocalization;
import com.platform.content.entity.LocalizableContentType;
import com.platform.content.repository.ContentLocalizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages translated content fields (V12's content_localizations) - the mechanism
 * that actually translates lesson/activity/question content, entirely separate from
 * the frontend's UI-chrome i18n. See ContentLocalization's javadoc for that
 * boundary.
 */
@Service
public class ContentLocalizationService {

    private final ContentLocalizationRepository repository;

    public ContentLocalizationService(ContentLocalizationRepository repository) {
        this.repository = repository;
    }

    /** Upsert: creates a new translation, or updates the existing one for this exact (content, locale, field) triple. */
    @Transactional
    public ContentLocalization setText(LocalizableContentType contentType, UUID contentId, String locale,
                                        String fieldName, String text) {
        return repository.findByContentTypeAndContentIdAndLocaleAndFieldName(contentType, contentId, locale, fieldName)
                .map(existing -> {
                    existing.updateText(text);
                    return existing;
                })
                .orElseGet(() -> repository.save(ContentLocalization.create(contentType, contentId, locale, fieldName, text)));
    }

    /** All translated fields for one content item in one locale, as a fieldName -&gt; text map - what a localized API response needs. */
    @Transactional(readOnly = true)
    public Map<String, String> getLocalizedFields(LocalizableContentType contentType, UUID contentId, String locale) {
        List<ContentLocalization> rows = repository.findByContentTypeAndContentIdAndLocale(contentType, contentId, locale);
        return rows.stream().collect(Collectors.toMap(
                ContentLocalization::getFieldName, ContentLocalization::getLocalizedText, (a, b) -> b, LinkedHashMap::new));
    }

}