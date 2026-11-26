package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Compares two values. Each side is text with placeholders, so it can be a literal, a context value, a pnLibrary
 * placeholder or an external one.
 *
 * <pre>{@code
 * type: compare
 * left: '%vault_eco_balance%'
 * operator: greater-or-equal
 * right: 100
 * }</pre>
 */
public final class CompareRequirement implements Requirement {
    private final String left;
    private final CompareOperator operator;
    private final String right;
    private final boolean ignoreCase;

    /** Creates it with every setting at its default. */
    public CompareRequirement() {
        this("", CompareOperator.EQUALS, "", true);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public CompareRequirement(@NotNull String left) {
        this(left, CompareOperator.EQUALS, "", true);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public CompareRequirement(@NotNull String left, @NotNull CompareOperator operator) {
        this(left, operator, "", true);
    }

    /** Creates it with the first 3 settings; the rest keep their defaults. */
    public CompareRequirement(@NotNull String left, @NotNull CompareOperator operator, @NotNull String right) {
        this(left, operator, right, true);
    }

    /** Creates it with every setting given. */
    public CompareRequirement(@NotNull String left, @NotNull CompareOperator operator, @NotNull String right, boolean ignoreCase) {
        this.left = left;
        this.operator = operator;
        this.right = right;
        this.ignoreCase = ignoreCase;
    }

    /** First side. */
    @NotNull
    public String getLeft() {
        return left;
    }

    /** Relation between the sides. */
    @NotNull
    public CompareOperator getOperator() {
        return operator;
    }

    /** Second side. */
    @NotNull
    public String getRight() {
        return right;
    }

    /** Whether text is compared without case sensitivity. */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    private boolean holds(String a, String b, BigDecimal x, BigDecimal y, RequirementContext context) {
        boolean numbers = x != null && y != null;
        switch (operator) {
            case EQUALS:
                return numbers ? x.compareTo(y) == 0 : same(a, b);
            case NOT_EQUALS:
                return numbers ? x.compareTo(y) != 0 : !same(a, b);
            case GREATER:
                return x.compareTo(y) > 0;
            case GREATER_OR_EQUAL:
                return x.compareTo(y) >= 0;
            case LESS:
                return x.compareTo(y) < 0;
            case LESS_OR_EQUAL:
                return x.compareTo(y) <= 0;
            case CONTAINS:
                return TextMatching.contains(a, b, ignoreCase);
            case STARTS_WITH:
                return TextMatching.startsWith(a, b, ignoreCase);
            case ENDS_WITH:
                return TextMatching.endsWith(a, b, ignoreCase);
            default:
                return matches(a, b, context);
        }
    }

    private boolean same(String a, String b) {
        return ignoreCase ? a.equalsIgnoreCase(b) : a.equals(b);
    }

    private boolean matches(String text, String pattern, RequirementContext context) {
        try {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
            return Pattern.compile(pattern, flags).matcher(text).matches();
        } catch (IllegalArgumentException error) {
            context.getLogger().warning("Invalid regular expression '" + pattern + "': " + error.getMessage());
            return false;
        }
    }

    private static BigDecimal decimal(String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        String a = context.text(left).trim();
        String b = context.text(right).trim();
        BigDecimal x = decimal(a);
        BigDecimal y = decimal(b);
        if (operator.isNumeric() && (x == null || y == null)) {
            return new Verdict.Unmet("'" + (x == null ? a : b) + "' is not a number");
        }
        boolean met = holds(a, b, x, y, context);
        String relation = operator.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Verdict.of(met, a + " is not " + relation + " " + b);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CompareRequirement)) {
            return false;
        }
        CompareRequirement that = (CompareRequirement) other;
        return Objects.equals(left, that.left)
                && Objects.equals(operator, that.operator)
                && Objects.equals(right, that.right)
                && ignoreCase == that.ignoreCase;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, operator, right, ignoreCase);
    }

    @NotNull
    @Override
    public String toString() {
        return "CompareRequirement(left=" + left + ", operator=" + operator + ", right=" + right + ", ignoreCase=" + ignoreCase + ")";
    }
}
