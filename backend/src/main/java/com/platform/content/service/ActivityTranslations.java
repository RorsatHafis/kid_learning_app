package com.platform.content.service;

import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.ContentLocalization;
import com.platform.content.entity.LocalizableContentType;
import com.platform.content.repository.ContentLocalizationRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * An activity version's Khmer (LOCALIZATION.md "Content in two languages", slice 2): {@code title}, {@code instructions},
 * {@code introduction} and {@code introductionImageDescription}, kept in {@code content_localizations} under the
 * version. The title is kept per version because it is shown with that version. As for questions
 * ({@link QuestionTranslations}): saved beside the English, carried to the next version, frozen when published, and a
 * child reading Khmer gets the activity's own words in Khmer only when they are all translated (otherwise in English).
 */
@Service
public class ActivityTranslations {

    private static final LocalizableContentType TYPE = LocalizableContentType.ACTIVITY_VERSION;

    private final ContentLocalizationRepository repository;

    public ActivityTranslations(ContentLocalizationRepository repository) {
        this.repository = repository;
    }

    public record Khmer(String title, String instructions, String introduction, String introductionImageDescription) {

        public boolean isEmpty() {
            return title == null && instructions == null && introduction == null && introductionImageDescription == null;
        }
    }

    @Transactional(readOnly = true)
    public Khmer of(UUID versionId) {
        String title = null;
        String instructions = null;
        String introduction = null;
        String description = null;
        for (ContentLocalization row : repository.findByContentTypeAndContentIdAndLocale(TYPE, versionId, QuestionTranslations.KHMER)) {
            switch (row.getFieldName()) {
                case "title" -> title = row.getLocalizedText();
                case "instructions" -> instructions = row.getLocalizedText();
                case "introduction" -> introduction = row.getLocalizedText();
                case "introductionImageDescription" -> description = row.getLocalizedText();
                default -> {
                    // another field: not one this version shows
                }
            }
        }
        return new Khmer(title, instructions, introduction, description);
    }

    /** Complete: the title, and every one of the activity's own words the English has. */
    public static boolean complete(Activity activity, ActivityVersion version, Khmer khmer) {
        return !QuestionTranslations.blank(khmer.title())
                && (QuestionTranslations.blank(version.getInstructions()) || !QuestionTranslations.blank(khmer.instructions()))
                && (QuestionTranslations.blank(version.getIntroduction()) || !QuestionTranslations.blank(khmer.introduction()))
                && (QuestionTranslations.blank(version.getIntroductionImageDescription())
                    || !QuestionTranslations.blank(khmer.introductionImageDescription()));
    }

    /** The Khmer to give the person asking, if they read Khmer and the activity's Khmer is complete. */
    @Transactional(readOnly = true)
    public Optional<Khmer> forReader(Activity activity, ActivityVersion version) {
        if (!QuestionTranslations.KHMER.equals(LocaleContextHolder.getLocale().getLanguage())) {
            return Optional.empty();
        }
        Khmer khmer = of(version.getId());
        return complete(activity, version, khmer) ? Optional.of(khmer) : Optional.empty();
    }

    /** Sets one field's Khmer on a draft; blank removes it (not translated). */
    @Transactional
    public void set(UUID versionId, String field, String text) {
        repository.findByContentTypeAndContentIdAndLocaleAndFieldName(TYPE, versionId, QuestionTranslations.KHMER, field)
                .ifPresent(existing -> {
                    repository.delete(existing);
                    repository.flush();
                });
        if (!QuestionTranslations.blank(text)) {
            repository.save(ContentLocalization.create(TYPE, versionId, QuestionTranslations.KHMER, field, text.strip()));
        }
    }

    /** Carries a version's Khmer to the next version (or a copy). */
    @Transactional
    public void copy(UUID fromVersionId, UUID toVersionId) {
        Khmer khmer = of(fromVersionId);
        set(toVersionId, "title", khmer.title());
        set(toVersionId, "instructions", khmer.instructions());
        set(toVersionId, "introduction", khmer.introduction());
        set(toVersionId, "introductionImageDescription", khmer.introductionImageDescription());
    }
}
