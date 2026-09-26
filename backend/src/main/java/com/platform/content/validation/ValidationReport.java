package com.platform.content.validation;

import java.util.List;

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

    /** {@code code} is stable for clients; {@code message} is for the author. */
    public record Issue(String code, String message) {
    }

}
