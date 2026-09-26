package com.platform.content.validation;

/**
 * Publishing refused: the draft has at least one blocking issue. A conflict with the
 * version's current state, like the lifecycle rules it replaces, hence an
 * {@link IllegalStateException}; mapped to 409 with the whole report.
 */
public class ContentValidationException extends IllegalStateException {

    private final ValidationReport report;

    public ContentValidationException(String what, ValidationReport report) {
        super(what + " cannot be published: " + report.errors().stream()
                .map(ValidationReport.Issue::message).reduce((a, b) -> a + "; " + b).orElse("invalid"));
        this.report = report;
    }

    public ValidationReport getReport() {
        return report;
    }

}
