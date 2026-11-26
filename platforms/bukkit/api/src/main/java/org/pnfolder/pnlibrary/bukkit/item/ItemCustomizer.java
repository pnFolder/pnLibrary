package org.pnfolder.pnlibrary.bukkit.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Anything {@link ItemSpec} has no field for: flags, custom model data, persistent data. Runs last, on the created item. */
@FunctionalInterface
public interface ItemCustomizer {

    /** Changes {@code item} in place. */
    void apply(@NotNull ItemStack item);
}
