package org.pnfolder.pnlibrary.core.placeholders.format;

import org.pnfolder.pnlibrary.core.placeholders.address.ArgumentTypes;

/** A default format taken from {@code @UseFormat} on a class, for every value of {@code type} ({@code null}: any). */
public final class FormatRule {
    private final String name;
    private final Class<?> type;

    public FormatRule(String name, Class<?> type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    /** Whether the rule covers values of {@code valueType}. */
    public boolean covers(Class<?> valueType) {
        return type == null || ArgumentTypes.boxed(type).isAssignableFrom(valueType);
    }
}
