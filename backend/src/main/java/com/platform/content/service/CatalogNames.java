package com.platform.content.service;

import com.platform.content.entity.ContentLocalization;
import com.platform.content.entity.LocalizableContentType;
import com.platform.content.repository.ContentLocalizationRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Khmer of catalog names (LOCALIZATION.md "Content in two languages", slice 3, V81): a subject's name, a skill's
 * name and description, a learning objective's (chapter's) title and description, a curriculum's name - kept in
 * {@code content_localizations} beside the English on each row, field {@code name} and {@code description}.
 * <p>
 * A name is one self-contained label, so it is given in Khmer to a Khmer reader whenever its Khmer is written, and in
 * English otherwise - per name, unlike a question, whose parts must all be in one language (rule L-1).
 * <p>
 * For a Khmer reader, what has been read is kept for the rest of the transaction, and {@link #prefetch} reads many rows
 * in one query, so a page naming many skills costs one query, not one per name (ReadQueryScalingIntegrationTests).
 * An English reader costs none.
 */
@Service
public class CatalogNames {

    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    /** The key this transaction's names are kept under (TransactionSynchronizationManager). */
    private static final Object CACHE = CatalogNames.class.getName() + ".cache";

    private final ContentLocalizationRepository repository;

    public CatalogNames(ContentLocalizationRepository repository) {
        this.repository = repository;
    }

    /** A catalog row's Khmer name and description as written so far (either may be null). */
    public record Khmer(String name, String description) {

        public static final Khmer NONE = new Khmer(null, null);
    }

    @Transactional(readOnly = true)
    public Khmer of(LocalizableContentType type, UUID id) {
        Map<String, Khmer> cache = cache();
        if (cache != null) {
            Khmer known = cache.get(key(type, id));
            if (known != null) {
                return known;
            }
        }
        Khmer khmer = read(type, id);
        if (cache != null) {
            cache.put(key(type, id), khmer);
        }
        return khmer;
    }

    /**
     * For a Khmer reader, reads the Khmer of many rows in one query and keeps it for the rest of the transaction, so the
     * {@link #name} and {@link #description} calls that follow cost nothing. Does nothing for an English reader, or
     * outside a transaction (there is nowhere to keep it).
     */
    @Transactional(readOnly = true)
    public void prefetch(LocalizableContentType type, Collection<UUID> ids) {
        Map<String, Khmer> cache = cache();
        if (!readerIsKhmer() || cache == null) {
            return;
        }
        List<UUID> missing = ids.stream().filter(id -> id != null && !cache.containsKey(key(type, id))).distinct().toList();
        if (missing.isEmpty()) {
            return;
        }
        Map<UUID, Khmer> found = of(type, missing);
        missing.forEach(id -> cache.put(key(type, id), found.getOrDefault(id, Khmer.NONE)));
    }

    private Khmer read(LocalizableContentType type, UUID id) {
        String name = null;
        String description = null;
        for (ContentLocalization row : repository.findByContentTypeAndContentIdAndLocale(type, id, QuestionTranslations.KHMER)) {
            if (NAME.equals(row.getFieldName())) {
                name = row.getLocalizedText();
            } else if (DESCRIPTION.equals(row.getFieldName())) {
                description = row.getLocalizedText();
            }
        }
        return new Khmer(name, description);
    }

    /** The Khmer of many rows of one type, by id (rows without any are absent). */
    @Transactional(readOnly = true)
    public Map<UUID, Khmer> of(LocalizableContentType type, Collection<UUID> ids) {
        Map<UUID, Khmer> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (ContentLocalization row : repository.findByContentTypeAndContentIdInAndLocale(type, ids, QuestionTranslations.KHMER)) {
            Khmer current = result.getOrDefault(row.getContentId(), Khmer.NONE);
            result.put(row.getContentId(), NAME.equals(row.getFieldName()) ? new Khmer(row.getLocalizedText(), current.description())
                    : DESCRIPTION.equals(row.getFieldName()) ? new Khmer(current.name(), row.getLocalizedText()) : current);
        }
        return result;
    }

    /** Replaces a row's Khmer name and description; blank removes one (not translated). */
    @Transactional
    public void set(LocalizableContentType type, UUID id, String name, String description) {
        put(type, id, NAME, name);
        put(type, id, DESCRIPTION, description);
        Map<String, Khmer> cache = cache();
        if (cache != null) {
            cache.remove(key(type, id));
        }
    }

    /** Whether the person asking reads Khmer (the request's language, which follows their own). */
    public static boolean readerIsKhmer() {
        return QuestionTranslations.KHMER.equals(LocaleContextHolder.getLocale().getLanguage());
    }

    /** A name as the reader gets it: its Khmer for a Khmer reader when written, otherwise as written. */
    @Transactional(readOnly = true)
    public String name(LocalizableContentType type, UUID id, String english) {
        if (id == null || !readerIsKhmer()) {
            return english;
        }
        String khmer = of(type, id).name();
        return khmer == null ? english : khmer;
    }

    /** A description as the reader gets it (the same rule as {@link #name}). */
    @Transactional(readOnly = true)
    public String description(LocalizableContentType type, UUID id, String english) {
        if (id == null || !readerIsKhmer()) {
            return english;
        }
        String khmer = of(type, id).description();
        return khmer == null ? english : khmer;
    }

    /** Many names as the reader gets them, in one query: id -&gt; name (the English where no Khmer is written). */
    @Transactional(readOnly = true)
    public Map<UUID, String> names(LocalizableContentType type, Map<UUID, String> english) {
        if (!readerIsKhmer() || english.isEmpty()) {
            return english;
        }
        Map<UUID, Khmer> khmer = of(type, english.keySet());
        Map<UUID, String> result = new HashMap<>(english);
        khmer.forEach((id, k) -> {
            if (k.name() != null) {
                result.put(id, k.name());
            }
        });
        return result;
    }

    private void put(LocalizableContentType type, UUID id, String field, String text) {
        repository.findByContentTypeAndContentIdAndLocaleAndFieldName(type, id, QuestionTranslations.KHMER, field).ifPresent(existing -> {
            repository.delete(existing);
            repository.flush();
        });
        if (!QuestionTranslations.blank(text)) {
            repository.save(ContentLocalization.create(type, id, QuestionTranslations.KHMER, field, text.strip()));
        }
    }

    private static String key(LocalizableContentType type, UUID id) {
        return type.name() + ":" + id;
    }

    /** What this transaction has read so far, or null outside one. */
    @SuppressWarnings("unchecked")
    private static Map<String, Khmer> cache() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        Map<String, Khmer> cache = (Map<String, Khmer>) TransactionSynchronizationManager.getResource(CACHE);
        if (cache == null) {
            Map<String, Khmer> created = new HashMap<>();
            TransactionSynchronizationManager.bindResource(CACHE, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(CACHE);
                }
            });
            cache = created;
        }
        return cache;
    }

    static List<String> fields() {
        return List.of(NAME, DESCRIPTION);
    }
}
