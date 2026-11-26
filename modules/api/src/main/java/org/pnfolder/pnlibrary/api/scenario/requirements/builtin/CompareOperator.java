package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

/** How {@link CompareRequirement} relates its two sides. */
public enum CompareOperator {
    /** Equal; numbers are compared as numbers, anything else as text. */
    EQUALS(false),
    /** Not equal. */
    NOT_EQUALS(false),
    /** Left is greater than right; both must be numbers. */
    GREATER(true),
    /** Left is greater than or equal to right; both must be numbers. */
    GREATER_OR_EQUAL(true),
    /** Left is less than right; both must be numbers. */
    LESS(true),
    /** Left is less than or equal to right; both must be numbers. */
    LESS_OR_EQUAL(true),
    /** Left text contains right text. */
    CONTAINS(false),
    /** Left text starts with right text. */
    STARTS_WITH(false),
    /** Left text ends with right text. */
    ENDS_WITH(false),
    /** Left text matches the regular expression on the right as a whole. */
    MATCHES(false);

    private final boolean numeric;

    CompareOperator(boolean numeric) {
        this.numeric = numeric;
    }

    boolean isNumeric() {
        return numeric;
    }
}
