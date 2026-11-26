package org.pnfolder.pnlibrary.api.scenario.expression;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Value rules shared by the parser and the built-in functions: what counts as a number, as true, and as text. */
final class ExpressionValues {
    private static final Set<String> FALSE_WORDS = new HashSet<>(Arrays.asList("false", "0", "no", "off", "disabled"));

    private ExpressionValues() {
    }

    static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double) {
            double number = (Double) value;
            boolean whole = !Double.isNaN(number) && !Double.isInfinite(number) && number == Math.rint(number)
                    && Math.abs(number) < 1e15;
            return whole ? Long.toString((long) number) : Double.toString(number);
        }
        return value.toString();
    }

    static Double number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            return parseDouble(((String) value).trim());
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1.0 : 0.0;
        }
        return null;
    }

    static Double parseDouble(String text) {
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }
        if (value instanceof String) {
            String text = (String) value;
            return !isBlank(text) && !FALSE_WORDS.contains(text.trim().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    static double required(Object value) {
        Double number = number(value);
        if (number == null) {
            throw new ExpressionException("'" + format(value) + "' is not a number");
        }
        return number;
    }

    static Object normalize(Object value) {
        return value instanceof Number ? (Object) ((Number) value).doubleValue() : value;
    }

    static boolean isBlank(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isWhitespace(ch) && !Character.isSpaceChar(ch)) {
                return false;
            }
        }
        return true;
    }

    static boolean containsIgnoreCase(String text, String part) {
        for (int i = 0; i + part.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, part, 0, part.length())) {
                return true;
            }
        }
        return false;
    }

    static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    static boolean endsWithIgnoreCase(String text, String suffix) {
        return text.regionMatches(true, text.length() - suffix.length(), suffix, 0, suffix.length());
    }
}
