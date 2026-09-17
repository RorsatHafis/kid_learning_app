package com.platform.content.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * One translated field of one content version (e.g. lesson_version X's "body" in
 * "km"). This is what actually owns translating lesson/activity/question content
 * (Khmer, English, ...) - entirely separate from the frontend's UI-chrome i18n
 * (nav labels, buttons), which never touches this table. Polymorphic reference
 * (contentType + contentId) with no real FK - see V12's migration comment for that
 * trade-off. Maps 1:1 to {@code content_localizations} (V12 migration).
 */
@Entity
@Table(name = "content_localizations")
public class ContentLocalization extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, updatable = false, length = 30)
    private LocalizableContentType contentType;

    @Column(name = "content_id", nullable = false, updatable = false)
    private UUID contentId;

    @NotBlank
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$")
    @Column(name = "locale", nullable = false, updatable = false, length = 10)
    private String locale;

    @NotBlank
    @Column(name = "field_name", nullable = false, updatable = false, length = 60)
    private String fieldName;

    @NotBlank
    @Column(name = "localized_text", nullable = false, columnDefinition = "text")
    private String localizedText;

    protected ContentLocalization() {
        // JPA
    }

    private ContentLocalization(LocalizableContentType contentType, UUID contentId, String locale,
                                 String fieldName, String localizedText) {
        this.contentType = contentType;
        this.contentId = contentId;
        this.locale = locale;
        this.fieldName = fieldName;
        this.localizedText = localizedText;
    }

    public static ContentLocalization create(LocalizableContentType contentType, UUID contentId, String locale,
                                              String fieldName, String localizedText) {
        Assert.notNull(contentType, "contentType must not be null");
        Assert.notNull(contentId, "contentId must not be null");
        Assert.hasText(locale, "locale must not be blank");
        Assert.isTrue(locale.matches("^[a-z]{2}(-[A-Z]{2})?$"), "locale must be a BCP-47-style tag, e.g. 'en' or 'km'");
        Assert.hasText(fieldName, "fieldName must not be blank");
        Assert.hasText(localizedText, "localizedText must not be blank");
        return new ContentLocalization(contentType, contentId, locale, fieldName, localizedText);
    }

    public void updateText(String newText) {
        Assert.hasText(newText, "newText must not be blank");
        this.localizedText = newText;
    }

    public LocalizableContentType getContentType() {
        return contentType;
    }

    public UUID getContentId() {
        return contentId;
    }

    public String getLocale() {
        return locale;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getLocalizedText() {
        return localizedText;
    }

}