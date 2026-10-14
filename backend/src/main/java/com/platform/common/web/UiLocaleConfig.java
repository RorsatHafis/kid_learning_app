package com.platform.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * A request's language is its Accept-Language, narrowed to the interface languages (English, Khmer).
 * Without the header, or asking only for another language, it is English - never the server's own
 * locale, which would make the same request read differently on another machine.
 */
@Configuration
public class UiLocaleConfig {

    /** Shared with the security filters, which answer before Spring MVC resolves a locale. */
    static final AcceptHeaderLocaleResolver RESOLVER = new AcceptHeaderLocaleResolver();

    static {
        RESOLVER.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("km")));
        RESOLVER.setDefaultLocale(Locale.ENGLISH);
    }

    @Bean
    public LocaleResolver localeResolver() {
        return RESOLVER;
    }

}
