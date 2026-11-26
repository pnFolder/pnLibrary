package org.pnfolder.pnlibrary.bukkit.item;

import org.jetbrains.annotations.NotNull;

/** Reads one extra configuration field into the spec being built, usually through {@link ItemSpec.Builder#customize}. */
@FunctionalInterface
public interface ItemField {

    /** Applies the raw configuration {@code value} (a string, number, list, map or section) to {@code item}. */
    void read(@NotNull ItemSpec.Builder item, @NotNull Object value);
}
