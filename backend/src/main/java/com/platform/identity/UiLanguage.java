package com.platform.identity;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Set;

/**
 * The languages the app's own words are offered in - English and Khmer. This is the interface language
 * only: learning content keeps the language its educators wrote it in, whatever an account chooses.
 */
public final class UiLanguage {

    public static final String ENGLISH = "en";
    public static final String KHMER = "km";
    public static final Set<String> SUPPORTED = Set.of(ENGLISH, KHMER);
    /** For request validation: exactly a supported code. */
    public static final String PATTERN = "en|km";

    private UiLanguage() {
    }

    public static boolean isSupported(String language) {
        return language != null && SUPPORTED.contains(language);
    }

    /** A supported code, or English when absent or unknown. */
    public static String orDefault(String language) {
        return isSupported(language) ? language : ENGLISH;
    }

    /** The language of the request being handled (its Accept-Language, as Spring resolved it). */
    public static String ofCurrentRequest() {
        Locale locale = LocaleContextHolder.getLocale();
        return KHMER.equals(locale.getLanguage()) ? KHMER : ENGLISH;
    }

    public static boolean isKhmer(String language) {
        return KHMER.equals(language);
    }

}
