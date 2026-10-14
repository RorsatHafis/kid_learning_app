package com.platform.content.validation;

import java.util.List;
import java.util.Map;

/**
 * What stands between a draft and learners. {@code errors} block publishing - each is
 * a way the published content would fail a child (unplayable, unscorable, or
 * producing no learning evidence). {@code warnings} are allowed but worth an author's
 * attention.
 */
public record ValidationReport(List<Issue> errors, List<Issue> warnings) {

    public ValidationReport {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean publishable() {
        return errors.isEmpty();
    }

    /**
     * {@code code} is stable for clients; {@code message} is the English sentence for logs and
     * for clients that don't know the code; {@code values} are what the sentence names (a skill,
     * a question's position, a count), so a client can say it in the author's own language.
     */
    public record Issue(String code, String message, Map<String, Object> values) {

        public Issue {
            values = Map.copyOf(values);
        }

        public Issue(String code, String message) {
            this(code, message, Map.of());
        }

    }

}
