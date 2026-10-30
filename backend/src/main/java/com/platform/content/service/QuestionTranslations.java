package com.platform.content.service;

import com.platform.content.entity.ContentLocalization;
import com.platform.content.entity.LocalizableContentType;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ContentLocalizationRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A question version's Khmer, beside its English (LOCALIZATION.md "Content in two languages"). The version's own
 * columns are its English; its Khmer is in {@code content_localizations} (V12) under the version, one row per field:
 * {@code prompt}, {@code hint}, {@code explanation}, {@code promptImageDescription}, and {@code option:<id>} for each
 * choice, pair side, item or group. Written together with the English in one save, so neither language can
 * overwrite the other; carried to every copy of the version (a new draft, a duplicate, a shared lesson's copy) with
 * the options' ids mapped to the copy's.
 * <p>
 * <b>What a child gets</b>: a child whose language is Khmer is given a question in Khmer only when its Khmer is
 * complete - every field the English has, and every option's label - and in English otherwise, never half one and
 * half the other. SHORT_ANSWER isn't translated: it is scored against the author's own words, so a Khmer prompt
 * would mark a Khmer answer wrong.
 */
@Service
public class QuestionTranslations {

    public static final String KHMER = "km";
    private static final LocalizableContentType TYPE = LocalizableContentType.QUESTION_VERSION;
    private static final String OPTION = "option:";

    private final ContentLocalizationRepository repository;

    public QuestionTranslations(ContentLocalizationRepository repository) {
        this.repository = repository;
    }

    /** A version's Khmer as written so far; {@code options}: option id -&gt; label. Any field may be missing. */
    public record Khmer(String prompt, String hint, String explanation, String promptImageDescription, Map<UUID, String> options) {

        public static final Khmer NONE = new Khmer(null, null, null, null, Map.of());

        public boolean isEmpty() {
            return prompt == null && hint == null && explanation == null && promptImageDescription == null && options.isEmpty();
        }

        public String label(QuestionOption option) {
            return options.get(option.getId());
        }
    }

    /** Whether a question of this type can carry Khmer at all. */
    public static boolean translatable(QuestionType type) {
        return type != QuestionType.SHORT_ANSWER;
    }

    @Transactional(readOnly = true)
    public Khmer of(UUID versionId) {
        return fromRows(repository.findByContentTypeAndContentIdAndLocale(TYPE, versionId, KHMER));
    }

    /** The Khmer of many versions in one query, by version id (a version without any is absent). */
    @Transactional(readOnly = true)
    public Map<UUID, Khmer> of(java.util.Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByContentTypeAndContentIdInAndLocale(TYPE, versionIds, KHMER).stream()
                .collect(java.util.stream.Collectors.groupingBy(ContentLocalization::getContentId))
                .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> fromRows(entry.getValue())));
    }

    /** How far a version's Khmer has got, as the Studio lists it (the bank). */
    public enum Status { NONE, PARTLY, COMPLETE, NOT_TRANSLATABLE }

    public static Status status(QuestionType type, QuestionVersion version, List<QuestionOption> options, Khmer khmer) {
        if (!translatable(type)) {
            return Status.NOT_TRANSLATABLE;
        }
        if (khmer == null || khmer.isEmpty()) {
            return Status.NONE;
        }
        return complete(type, version, options, khmer) ? Status.COMPLETE : Status.PARTLY;
    }

    private static Khmer fromRows(List<ContentLocalization> rows) {
        String prompt = null;
        String hint = null;
        String explanation = null;
        String description = null;
        Map<UUID, String> options = new LinkedHashMap<>();
        for (ContentLocalization row : rows) {
            String field = row.getFieldName();
            String text = row.getLocalizedText();
            switch (field) {
                case "prompt" -> prompt = text;
                case "hint" -> hint = text;
                case "explanation" -> explanation = text;
                case "promptImageDescription" -> description = text;
                default -> {
                    if (field.startsWith(OPTION)) {
                        try {
                            options.put(UUID.fromString(field.substring(OPTION.length())), text);
                        } catch (IllegalArgumentException ignored) {
                            // not one of ours
                        }
                    }
                }
            }
        }
        return new Khmer(prompt, hint, explanation, description, options);
    }

    /** Complete: every field the English has, and every option's label, has its Khmer. */
    public static boolean complete(QuestionType type, QuestionVersion version, List<QuestionOption> options, Khmer khmer) {
        if (!translatable(type) || khmer.isEmpty() || blank(khmer.prompt())) {
            return false;
        }
        if (!blank(version.getHint()) && blank(khmer.hint())) {
            return false;
        }
        if (!blank(version.getExplanation()) && blank(khmer.explanation())) {
            return false;
        }
        if (!blank(version.getPromptImageDescription()) && blank(khmer.promptImageDescription())) {
            return false;
        }
        return options.stream().filter(o -> !blank(o.getLabel())).allMatch(o -> !blank(khmer.label(o)));
    }

    /**
     * The Khmer to give the person asking, if they read Khmer (the request's language) and the question's Khmer is
     * complete; empty means English, as written.
     */
    @Transactional(readOnly = true)
    public Optional<Khmer> forReader(QuestionType type, QuestionVersion version, List<QuestionOption> options) {
        return forLanguage(LocaleContextHolder.getLocale().getLanguage(), type, version, options);
    }

    @Transactional(readOnly = true)
    public Optional<Khmer> forLanguage(String language, QuestionType type, QuestionVersion version, List<QuestionOption> options) {
        if (!KHMER.equals(language) || !translatable(type)) {
            return Optional.empty();
        }
        Khmer khmer = of(version.getId());
        return complete(type, version, options, khmer) ? Optional.of(khmer) : Optional.empty();
    }

    /** Replaces a draft's Khmer: blank fields are left out (not translated yet). Options are keyed by their ids. */
    @Transactional
    public void write(UUID versionId, Khmer khmer) {
        repository.deleteByContentTypeAndContentIdAndLocale(TYPE, versionId, KHMER);
        repository.flush();
        put(versionId, "prompt", khmer.prompt());
        put(versionId, "hint", khmer.hint());
        put(versionId, "explanation", khmer.explanation());
        put(versionId, "promptImageDescription", khmer.promptImageDescription());
        khmer.options().forEach((id, label) -> put(versionId, OPTION + id, label));
    }

    /**
     * Copies a version's Khmer to a copy of it whose options were copied in the same structure (same order, sides,
     * pairs and groups), mapping each option's id to its copy's.
     */
    @Transactional
    public void copy(UUID fromVersionId, List<QuestionOption> fromOptions, UUID toVersionId, List<QuestionOption> toOptions) {
        Khmer khmer = of(fromVersionId);
        if (khmer.isEmpty()) {
            return;
        }
        Map<String, UUID> copies = new HashMap<>();
        toOptions.forEach(o -> copies.put(key(o), o.getId()));
        Map<UUID, String> options = new LinkedHashMap<>();
        for (QuestionOption original : fromOptions) {
            String label = khmer.label(original);
            UUID copy = copies.get(key(original));
            if (label != null && copy != null) {
                options.put(copy, label);
            }
        }
        write(toVersionId, new Khmer(khmer.prompt(), khmer.hint(), khmer.explanation(), khmer.promptImageDescription(), options));
    }

    /** What identifies an option within its version, whatever its id: its place, side and pair, role and group. */
    private static String key(QuestionOption option) {
        return option.getDisplayOrder() + "|" + option.getMatchSide() + "|" + option.getMatchPair() + "|" + option.getSortRole() + "|"
                + option.getSortGroup();
    }

    private void put(UUID versionId, String field, String text) {
        if (!blank(text)) {
            repository.save(ContentLocalization.create(TYPE, versionId, KHMER, field, text.strip()));
        }
    }

    static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    /** Keeps only the ids that exist (a draft's options), for a Khmer built from a request. */
    public static Map<UUID, String> onlyOptions(Map<UUID, String> options, List<QuestionOption> existing) {
        Map<UUID, String> kept = new LinkedHashMap<>();
        existing.forEach(o -> {
            String label = options.get(o.getId());
            if (label != null) {
                kept.put(o.getId(), label);
            }
        });
        return kept;
    }

    public static boolean same(String a, String b) {
        return Objects.equals(a, b);
    }
}
