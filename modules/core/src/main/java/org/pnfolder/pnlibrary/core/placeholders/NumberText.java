package org.pnfolder.pnlibrary.core.placeholders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

/** Writes numbers the way the viewer reads them. */
final class NumberText {
    private static final String NO_BREAK_SPACE = " ";
    private static final int[] ROMAN_SIZES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private NumberText() {
    }

    static boolean isRussian(ViewerInfo viewer) {
        return viewer.getLanguage().regionMatches(true, 0, "ru", 0, 2);
    }

    static String group(Number number, ViewerInfo viewer) {
        String text = new BigDecimal(number.toString()).toPlainString();
        int dot = text.indexOf('.');
        String integer = dot < 0 ? text : text.substring(0, dot);
        String fraction = dot < 0 ? "" : text.substring(dot + 1);
        String sign = integer.startsWith("-") ? "-" : "";
        String digits = integer.startsWith("-") ? integer.substring(1) : integer;
        boolean russian = isRussian(viewer);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                grouped.append(russian ? NO_BREAK_SPACE : ",");
            }
            grouped.append(digits.charAt(i));
        }
        return sign + grouped + (fraction.isEmpty() ? "" : (russian ? "," : ".") + fraction);
    }

    static String round(Number number, int places, ViewerInfo viewer) {
        int scale = Math.max(0, Math.min(8, places));
        String text = new BigDecimal(number.doubleValue()).setScale(scale, RoundingMode.HALF_UP).toPlainString();
        return isRussian(viewer) ? text.replace('.', ',') : text;
    }

    static String shorten(Number number, ViewerInfo viewer) {
        double value = number.doubleValue();
        double divider;
        String suffix;
        if (Math.abs(value) >= 1e9) {
            divider = 1e9;
            suffix = "B";
        } else if (Math.abs(value) >= 1e6) {
            divider = 1e6;
            suffix = "M";
        } else if (Math.abs(value) >= 1e3) {
            divider = 1e3;
            suffix = "K";
        } else {
            return String.valueOf(number.longValue());
        }
        return round(value / divider, 1, viewer) + suffix;
    }

    static String roman(int number) {
        if (number < 1 || number > 3999) {
            return String.valueOf(number);
        }
        int rest = number;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < ROMAN_SIZES.length; i++) {
            while (rest >= ROMAN_SIZES[i]) {
                text.append(ROMAN_SYMBOLS[i]);
                rest -= ROMAN_SIZES[i];
            }
        }
        return text.toString();
    }
}
