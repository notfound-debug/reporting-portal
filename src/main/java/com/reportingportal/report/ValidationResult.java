package com.reportingportal.report;

import java.util.Map;

/**
 * What ParamValidator returns: either valid parameters, or one error message per
 * field. Either way it carries the values as submitted, so the form can show
 * them again (always printed through c:out or fn:escapeXml).
 */
public final class ValidationResult {

    private final Map<String, String> submitted;
    private final Map<String, String> errors;
    private final ValidatedParams params;

    private ValidationResult(Map<String, String> submitted, Map<String, String> errors, ValidatedParams params) {
        this.submitted = Map.copyOf(submitted);
        this.errors = Map.copyOf(errors);
        this.params = params;
    }

    static ValidationResult valid(Map<String, String> submitted, ValidatedParams params) {
        return new ValidationResult(submitted, Map.of(), params);
    }

    static ValidationResult invalid(Map<String, String> submitted, Map<String, String> errors) {
        return new ValidationResult(submitted, errors, null);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** Field name -> value as typed (trimmed), for redisplaying the form. */
    public Map<String, String> getSubmitted() {
        return submitted;
    }

    /** Field name -> error message. Empty when valid. */
    public Map<String, String> getErrors() {
        return errors;
    }

    /** The validated parameters; null when not valid. */
    public ValidatedParams getParams() {
        return params;
    }
}
