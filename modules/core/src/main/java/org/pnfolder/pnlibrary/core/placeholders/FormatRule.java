package org.pnfolder.pnlibrary.core.placeholders;

/** A default format taken from {@code @UseFormat} on a class, for every value of {@code type} ({@code null}: any). */
final class FormatRule {
    private final String name;
    private final Class<?> type;

    FormatRule(String name, Class<?> type) {
        this.name = name;
        this.type = type;
    }

    String getName() {
        return name;
    }

    /** Whether the rule covers values of {@code valueType}. */
    boolean covers(Class<?> valueType) {
        return type == null || ArgumentTypes.boxed(type).isAssignableFrom(valueType);
    }
}
