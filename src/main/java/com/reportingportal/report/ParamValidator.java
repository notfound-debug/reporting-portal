package com.reportingportal.report;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server-side validation of report form parameters.
 *
 * It only looks at the parameters the report defines, by name, so anything
 * else in the request is ignored and can never reach SQL. Each value must pass
 * its type's rule (a format check first, then parsing, then a range or list
 * check) and is converted to the Java type that will be bound to the query.
 * All errors are collected, one message per field, not just the first.
 *
 * Takes the request's parameter map rather than the request itself, so it can
 * be unit-tested without a servlet container.
 */
public final class ParamValidator {

    public static final String SORT = "sort";
    public static final String DIRECTION = "dir";
    public static final String PAGE = "page";

    /** Longer input is rejected before any parsing. */
    static final int MAX_LENGTH = 100;

    /** The warehouse's date dimension covers 2016-01-01 to 2019-12-31. */
    static final YearMonth FIRST_MONTH = YearMonth.of(2016, 1);
    static final YearMonth LAST_MONTH = YearMonth.of(2019, 12);
    static final LocalDate FIRST_DATE = LocalDate.of(2016, 1, 1);
    static final LocalDate LAST_DATE = LocalDate.of(2019, 12, 31);

    static final int MAX_PAGE = 10_000;

    private static final Pattern MONTH = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("^\\d{1,4}$");
    private static final Pattern STATE_CODE = Pattern.compile("^[A-Z]{2}$");

    private ParamValidator() {
    }

    /** Thrown inside this class for one bad value; its message is shown next to the field. */
    private static final class InvalidValue extends Exception {
        InvalidValue(String message) {
            super(message);
        }
    }

    public static ValidationResult validate(ReportDefinition report, Map<String, String[]> input,
                                            List<String> categories) {
        Map<String, String> submitted = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>();

        for (ParamDef param : report.getParams()) {
            String value = readSingle(input, param.getName(), param.getLabel(), errors);
            if (value == null) {
                value = param.getDefaultValue();   // only COLUMN and ROW_LIMIT have defaults
            }
            if (value == null) {
                continue;                          // absent: no filter
            }
            if (param.getType() == ParamType.STATE) {
                value = value.toUpperCase(Locale.ROOT);
            }
            submitted.put(param.getName(), value);
            try {
                values.put(param.getName(), convert(param, value, categories));
            } catch (InvalidValue e) {
                errors.put(param.getName(), e.getMessage());
            }
        }

        checkRanges(report, values, errors);

        String sortColumn = null;
        SortDirection direction = null;
        if (report.isSortable()) {
            String sort = orDefault(readSingle(input, SORT, "Sort column", errors), report.getDefaultSort());
            String dir = orDefault(readSingle(input, DIRECTION, "Direction", errors),
                    report.getDefaultDirection().getParameterValue());
            submitted.put(SORT, sort);
            submitted.put(DIRECTION, dir);
            // The whitelist: the SQL gets the map's value, never the request string.
            sortColumn = report.getSortableColumns().get(sort);
            if (sortColumn == null) {
                errors.put(SORT, "Sort column must be one of: " + String.join(", ", report.getSortableColumns().keySet()) + ".");
            }
            direction = SortDirection.fromParameter(dir);
            if (direction == null) {
                errors.put(DIRECTION, "Direction must be asc or desc.");
            }
        }

        int page = 1;
        if (report.isPaged()) {
            String pageText = readSingle(input, PAGE, "Page", errors);
            if (pageText != null) {
                try {
                    page = wholeNumber(pageText, 1, MAX_PAGE, "Page");
                } catch (InvalidValue e) {
                    errors.put(PAGE, e.getMessage());
                }
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(submitted, errors);
        }
        // The canonical form of the parameters: every submitted value is valid at this point.
        return ValidationResult.valid(submitted, new ValidatedParams(values, submitted, sortColumn, direction, page));
    }

    /**
     * The trimmed single value of a parameter, or null if it is missing or blank.
     * The same name given twice is an error: it is not clear which one was meant.
     */
    private static String readSingle(Map<String, String[]> input, String name, String label,
                                     Map<String, String> errors) {
        String[] given = input.get(name);
        if (given == null || given.length == 0) {
            return null;
        }
        if (given.length > 1) {
            errors.put(name, label + " was given more than once.");
            return null;
        }
        String value = given[0].trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_LENGTH) {
            errors.put(name, label + " is too long.");
            return null;
        }
        return value;
    }

    /** Checks one value against its type's rule and returns what will be bound (or used) in SQL. */
    private static Object convert(ParamDef param, String value, List<String> categories) throws InvalidValue {
        String label = param.getLabel();
        switch (param.getType()) {
            case MONTH:
                return Date.valueOf(month(value, label).atDay(1));
            case DATE:
                return Date.valueOf(date(value, label));
            case INTEGER:
            case ROW_LIMIT:
                return wholeNumber(value, param.getMin(), param.getMax(), label);
            case STATE:
                if (!STATE_CODE.matcher(value).matches() || !BrazilStates.isValid(value)) {
                    throw new InvalidValue(label + " must be a Brazilian state code such as SP or RJ.");
                }
                return value;
            case CHOICE:
                if (!param.getChoices().contains(value)) {
                    throw new InvalidValue(label + " must be one of: " + String.join(", ", param.getChoices()) + ".");
                }
                return value;
            case CATEGORY:
                if (!categories.contains(value)) {
                    throw new InvalidValue(label + " is not a known product category.");
                }
                return value;
            case FLAG:
                if (!value.equals("on") && !value.equals("true")) {
                    throw new InvalidValue(label + " must be on or off.");
                }
                return Boolean.TRUE;
            case COLUMN:
                String column = param.getColumnOptions().get(value);
                if (column == null) {
                    throw new InvalidValue(label + " must be one of: "
                            + String.join(", ", param.getColumnOptions().keySet()) + ".");
                }
                return column;
            default:
                throw new IllegalStateException("Unhandled parameter type " + param.getType());
        }
    }

    private static YearMonth month(String value, String label) throws InvalidValue {
        String message = label + " must be a month between " + FIRST_MONTH + " and " + LAST_MONTH + ", written YYYY-MM.";
        if (!MONTH.matcher(value).matches()) {
            throw new InvalidValue(message);
        }
        YearMonth month = YearMonth.parse(value);
        if (month.isBefore(FIRST_MONTH) || month.isAfter(LAST_MONTH)) {
            throw new InvalidValue(message);
        }
        return month;
    }

    private static LocalDate date(String value, String label) throws InvalidValue {
        String message = label + " must be a real date between " + FIRST_DATE + " and " + LAST_DATE + ", written YYYY-MM-DD.";
        if (!DATE.matcher(value).matches()) {
            throw new InvalidValue(message);
        }
        LocalDate date;
        try {
            // LocalDate.parse uses ISO_LOCAL_DATE, which is strict: 2017-02-30 is rejected.
            date = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidValue(message);
        }
        if (date.isBefore(FIRST_DATE) || date.isAfter(LAST_DATE)) {
            throw new InvalidValue(message);
        }
        return date;
    }

    private static int wholeNumber(String value, int min, int max, String label) throws InvalidValue {
        String message = label + " must be a whole number from " + min + " to " + max + ".";
        // At most 4 digits, so parseInt can never overflow.
        if (!WHOLE_NUMBER.matcher(value).matches()) {
            throw new InvalidValue(message);
        }
        int number = Integer.parseInt(value);
        if (number < min || number > max) {
            throw new InvalidValue(message);
        }
        return number;
    }

    /** "From" must not be after "to"; the message goes on the "to" field. */
    private static void checkRanges(ReportDefinition report, Map<String, Object> values, Map<String, String> errors) {
        for (ReportDefinition.RangeRule rule : report.getRangeRules()) {
            Object from = values.get(rule.fromParam());
            Object to = values.get(rule.toParam());
            if (from instanceof Date fromDate && to instanceof Date toDate && fromDate.after(toDate)) {
                errors.put(rule.toParam(), report.param(rule.toParam()).getLabel()
                        + " must not be before " + report.param(rule.fromParam()).getLabel() + ".");
            }
        }
    }

    private static String orDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
