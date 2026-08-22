package ru.privatenull.pnlibrary.gui;

import java.util.Locale;

/** Publicly available inventory reveal styles. */
public enum GuiAnimationType {
    CENTER_OUT,
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
    DIAGONAL_DOWN,
    DIAGONAL_UP,
    NONE;

    public static GuiAnimationType fromConfig(String value) {
        if (value == null || value.isBlank()) return CENTER_OUT;
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "main", "center", "centre", "center_out", "centre_out" -> CENTER_OUT;
            case "left", "ltr", "left_to_right" -> LEFT_TO_RIGHT;
            case "right", "rtl", "right_to_left" -> RIGHT_TO_LEFT;
            case "top", "ttb", "top_to_bottom" -> TOP_TO_BOTTOM;
            case "bottom", "btt", "bottom_to_top" -> BOTTOM_TO_TOP;
            case "diagonal", "diagonal_down", "top_left" -> DIAGONAL_DOWN;
            case "diagonal_up", "bottom_left" -> DIAGONAL_UP;
            case "off", "disabled", "none" -> NONE;
            default -> CENTER_OUT;
        };
    }
}
